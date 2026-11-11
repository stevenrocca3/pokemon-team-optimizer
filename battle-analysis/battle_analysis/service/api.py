"""
The battle analysis API (FastAPI). Caddy sends /api/v1/analyze* here; everything else stays with the Spring app.

POST /api/v1/analyze                 upload a video -> 202 {job_id, ...}; the work runs on a Celery worker
GET  /api/v1/analyze/{job_id}        the job's status and progress
GET  /api/v1/analyze/{job_id}/events live status over Server-Sent Events
GET  /api/v1/analyze/{job_id}/result the turn_log and report, once complete
Errors are {"detail": "<a sentence a player can act on>"}, which the frontend's apiFetch shows as is.
"""
import json
import logging
import shutil
import time
import uuid
from functools import cache

import redis
from fastapi import FastAPI, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, StreamingResponse

from battle_analysis.service import tasks
from battle_analysis.service.config import settings
from battle_analysis.service.store import FINAL, JobStore

log = logging.getLogger(__name__)
app = FastAPI(title='DexPresso battle analysis', docs_url=None, redoc_url=None)

VIDEO_SUFFIXES = {'.mp4', '.mov', '.m4v'}
CHUNK = 1024 * 1024
MAX_STREAM_S = 3600  # a stream closes after an hour regardless; the page reconnects or polls the status


@cache
def get_store() -> JobStore:
    return JobStore(redis.Redis.from_url(settings.redis_url), settings.job_ttl_s)


def enqueue(job_id: str, path: str) -> None:
    tasks.enqueue(job_id, path)


def _client(request: Request) -> str:
    # Caddy sets X-Real-IP to the visitor's address (visitors can't spoof it through Caddy)
    return request.headers.get('X-Real-IP') or (request.client.host if request.client else 'unknown')


def _is_video(file: UploadFile, head: bytes) -> bool:
    suffix = '.' + (file.filename or '').rsplit('.', 1)[-1].lower() if '.' in (file.filename or '') else ''
    # MP4 and MOV both start with a size then "ftyp" (MOV can also open with "moov"/"wide"/"mdat")
    boxed = head[4:8] in (b'ftyp', b'moov', b'wide', b'mdat', b'free')
    return suffix in VIDEO_SUFFIXES and boxed


@app.post('/api/v1/analyze', status_code=202)
async def upload(request: Request, file: UploadFile):
    store = get_store()
    if not store.allow(_client(request), settings.uploads_per_hour):
        raise HTTPException(429, "You've started a lot of analyses this hour. Try again later.",
                            headers={'Retry-After': '3600'})
    head = await file.read(16)
    if not _is_video(file, head):
        raise HTTPException(415, 'That file isn’t a video we can read. Upload an MP4 or MOV screen recording.')
    settings.upload_dir.mkdir(parents=True, exist_ok=True)
    path = settings.upload_dir / f'{uuid.uuid4().hex}.video'
    size = len(head)
    try:
        with path.open('wb') as out:
            out.write(head)
            while chunk := await file.read(CHUNK):
                size += len(chunk)
                if size > settings.max_upload_bytes:
                    raise HTTPException(413, f'That video is over {settings.max_upload_bytes // (1024 * 1024)} MB. '
                                             'Trim it to just the battle and try again.')
                out.write(chunk)
    except BaseException:
        path.unlink(missing_ok=True)
        raise
    job_id = store.create()
    final = path.with_name(f'{job_id}{path.suffix}')
    shutil.move(path, final)
    try:
        enqueue(job_id, str(final))
    except Exception:
        log.exception('could not queue analysis job %s', job_id)
        final.unlink(missing_ok=True)
        store.fail(job_id, 'The analysis queue is down.')
        raise HTTPException(503, 'Battle analysis is busy or restarting. Try again in a few minutes.')
    base = f'/api/v1/analyze/{job_id}'
    return {'job_id': job_id, 'status': 'queued', 'status_url': base, 'events_url': f'{base}/events',
            'result_url': f'{base}/result'}


def _job(job_id: str) -> dict:
    status = get_store().get(job_id) if len(job_id) == 32 else None
    if not status:
        raise HTTPException(404, 'No analysis with that ID. Results are kept for 24 hours.')
    return status


@app.get('/api/v1/analyze/{job_id}')
def status(job_id: str):
    return _job(job_id)


@app.get('/api/v1/analyze/{job_id}/result')
def result(job_id: str):
    status = _job(job_id)
    if status['status'] != 'complete':
        return JSONResponse({'detail': 'This analysis isn’t finished yet.', 'status': status['status']}, status_code=409)
    data = get_store().get_result(job_id)
    if data is None:
        raise HTTPException(404, 'This analysis has expired. Results are kept for 24 hours.')
    return data


def _sse(status: dict) -> str:
    return f"event: status\ndata: {json.dumps(status)}\n\n"


def _stream(store: JobStore, job_id: str, first: dict, pubsub):
    """The current status, then every change the worker publishes, until the job ends; heartbeats in between."""
    try:
        yield _sse(first)
        if first['status'] in FINAL:
            return
        deadline = time.monotonic() + MAX_STREAM_S
        while time.monotonic() < deadline:
            msg = pubsub.get_message(ignore_subscribe_messages=True, timeout=settings.heartbeat_s)
            if msg is None:
                yield ': ping\n\n'
                continue
            status = json.loads(msg['data'])
            yield _sse(status)
            if status['status'] in FINAL:
                return
    finally:
        pubsub.close()


@app.get('/api/v1/analyze/{job_id}/events')
def events(job_id: str):
    """
    Server-Sent Events: `event: status` with the job's status JSON on every change (processing_cv, reconstructing,
    auditing_turns, then complete or failed, which ends the stream), and `: ping` comments while nothing changes.
    """
    store = get_store()
    pubsub = store.redis.pubsub()
    pubsub.subscribe(store.channel(job_id))  # before reading the status, so a change in between isn't missed
    try:
        first = _job(job_id)
    except HTTPException:
        pubsub.close()
        raise
    return StreamingResponse(_stream(store, job_id, first, pubsub), media_type='text/event-stream',
                             headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})


@app.get('/api/v1/analyze-health')
def health():
    return {'ok': True}
