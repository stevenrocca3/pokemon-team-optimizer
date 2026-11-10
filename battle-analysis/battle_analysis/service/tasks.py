"""
The worker: a Celery task per uploaded video, run by `celery -A battle_analysis.service.tasks worker`. One video at a
time per worker process (CV is CPU-heavy and the server is small); a job is acknowledged only once it's done, so a
worker that dies mid-job doesn't lose it.
"""
import logging
from functools import cache
from pathlib import Path

import redis
from celery import Celery

from battle_analysis.analysis.scoring import analyze_match
from battle_analysis.cv.pipeline import analyze_video
from battle_analysis.cv.reconstruct import NoBattleFound
from battle_analysis.service.config import settings
from battle_analysis.service.store import JobStore

log = logging.getLogger(__name__)

celery_app = Celery('battle_analysis', broker=settings.redis_url)
celery_app.conf.update(task_acks_late=True, worker_prefetch_multiplier=1, task_serializer='json',
                       accept_content=['json'], task_ignore_result=True, broker_connection_retry_on_startup=True)

CV_SHARE = 90  # the CV pass is the first 90% of the progress bar; the audits and score the rest


@cache
def get_store() -> JobStore:
    return JobStore(redis.Redis.from_url(settings.redis_url), settings.job_ttl_s)


def run_job(job_id: str, path: str, store: JobStore | None = None) -> None:
    store = store or get_store()
    try:
        store.update(job_id, status='processing_cv', progress=1)

        def progress(share: float, stage: str) -> None:
            store.update(job_id, status=stage, progress=max(1, int(share * CV_SHARE)))

        turn_log = analyze_video(path, job_id, progress)
        store.update(job_id, status='auditing_turns', progress=CV_SHARE + 2)
        report = analyze_match(turn_log)
        store.set_result(job_id, {'turn_log': turn_log, 'report': report})
    except NoBattleFound as e:
        store.fail(job_id, str(e))
    except Exception:
        log.exception('analysis job %s failed', job_id)
        store.fail(job_id, "Something went wrong reading this video. Try again, or try another recording.")
    finally:
        Path(path).unlink(missing_ok=True)  # the video is never kept


@celery_app.task(name='battle_analysis.analyze')
def analyze_task(job_id: str, path: str) -> None:
    run_job(job_id, path)


def enqueue(job_id: str, path: str) -> None:
    analyze_task.delay(job_id, path)
