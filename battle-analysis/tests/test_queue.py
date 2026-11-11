"""Issue #101: the background video processing pipeline and queue."""
import json
from pathlib import Path

import fakeredis
import pytest
from fastapi.testclient import TestClient

from battle_analysis.cv.reconstruct import NoBattleFound
from battle_analysis.service import api, tasks
from battle_analysis.service.store import JobStore

FIXTURE = Path(__file__).parent / 'fixtures/rec1_turn_log.json'
MP4_BYTES = b'\x00\x00\x00\x18ftypmp42' + b'\x00' * 2000  # an MP4 header; the API checks type, the worker decodes


@pytest.fixture
def env(tmp_path, monkeypatch):
    """The API and worker wired to an in-memory Redis, a temp upload folder, and a recorded queue."""
    redis = fakeredis.FakeRedis()
    store = JobStore(redis)
    queued = []
    monkeypatch.setattr(api, 'get_store', lambda: store)
    monkeypatch.setattr(api, 'enqueue', lambda job_id, path: queued.append((job_id, path)))
    monkeypatch.setattr(api.settings, 'upload_dir', tmp_path)
    monkeypatch.setattr(api.settings, 'max_upload_bytes', 10_000)
    monkeypatch.setattr(api.settings, 'uploads_per_hour', 5)
    return {'client': TestClient(api.app), 'store': store, 'queued': queued, 'dir': tmp_path, 'redis': redis}


def upload(client, name='battle.mp4', body=MP4_BYTES, kind='video/mp4', ip='1.2.3.4'):
    return client.post('/api/v1/analyze', files={'file': (name, body, kind)}, headers={'X-Real-IP': ip})


# Uploading a video answers at once with 202 and a job_id (32 hex chars) while the work waits in the queue: the job is
# queued, the queue got that job and the saved file, and the file holds exactly the uploaded bytes.
def test_upload_returns_a_job_id_at_once_and_queues_the_work(env):
    r = upload(env['client'])
    assert r.status_code == 202
    job_id = r.json()['job_id']
    assert len(job_id) == 32 and int(job_id, 16) >= 0
    assert r.json()['status'] == 'queued'
    [(queued_id, path)] = env['queued']
    assert queued_id == job_id
    assert Path(path).read_bytes() == MP4_BYTES


# The job's status can be polled: queued at 0% until a worker picks it up.
def test_status_of_a_queued_job(env):
    job_id = upload(env['client']).json()['job_id']
    status = env['client'].get(f'/api/v1/analyze/{job_id}').json()
    assert (status['status'], status['progress']) == ('queued', 0)


# A file that isn't a video (.txt) is refused with 415 and a sentence saying what to send; nothing is queued or kept.
def test_non_video_is_refused(env):
    r = upload(env['client'], name='notes.txt', body=b'hello', kind='text/plain')
    assert r.status_code == 415
    assert 'MP4 or MOV' in r.json()['detail']
    assert env['queued'] == [] and list(env['dir'].iterdir()) == []


# A video over the size limit (10 kB here) is refused with 413 and the partial file is deleted.
def test_oversized_upload_is_refused_and_removed(env):
    r = upload(env['client'], body=MP4_BYTES * 10)
    assert r.status_code == 413
    assert env['queued'] == [] and list(env['dir'].iterdir()) == []


# Each IP can start 5 analyses an hour; the 6th gets 429 (another IP still can).
def test_uploads_are_rate_limited_per_ip(env):
    for _ in range(5):
        assert upload(env['client']).status_code == 202
    assert upload(env['client']).status_code == 429
    assert upload(env['client'], ip='5.6.7.8').status_code == 202


# An unknown job is a 404, not an empty status.
def test_unknown_job_is_404(env):
    assert env['client'].get('/api/v1/analyze/' + 'f' * 32).status_code == 404


# The worker runs the CV pass and the analysis, reporting progress as it goes, then stores the result: here the CV
# pass is replaced by Steven's reconstructed rec 1, so the stored score is that battle's 88. The video is deleted.
def test_worker_processes_the_job_and_deletes_the_video(env, monkeypatch):
    job_id = upload(env['client']).json()['job_id']
    [(_, path)] = env['queued']
    seen = []

    def fake_cv(video, match_id, progress):
        progress(0.5, 'processing_cv')
        seen.append(env['store'].get(job_id)['progress'])
        return json.loads(FIXTURE.read_text())

    monkeypatch.setattr(tasks, 'analyze_video', fake_cv)
    tasks.run_job(job_id, path, store=env['store'])
    status = env['store'].get(job_id)
    assert status['status'] == 'complete' and status['progress'] == 100
    assert status['score'] == 88
    assert seen == [45]  # 50% through the CV pass is 45% overall (the CV pass is the first 90%)
    assert not Path(path).exists()
    result = env['client'].get(f'/api/v1/analyze/{job_id}/result').json()
    assert result['report']['score'] == 88 and result['turn_log']['user_team'] == ['Swampert', 'Cradily']


# A video with no battle in it fails the job with the reader's own explanation, and the video is still deleted.
def test_worker_failure_is_reported_and_cleans_up(env, monkeypatch):
    job_id = upload(env['client']).json()['job_id']
    [(_, path)] = env['queued']

    def no_battle(video, match_id, progress):
        raise NoBattleFound('No battle HUD found in this video.')

    monkeypatch.setattr(tasks, 'analyze_video', no_battle)
    tasks.run_job(job_id, path, store=env['store'])
    status = env['store'].get(job_id)
    assert status['status'] == 'failed' and status['error'] == 'No battle HUD found in this video.'
    assert not Path(path).exists()


# The result isn't there until the job completes: 409 while it's still queued.
def test_result_before_completion_is_409(env):
    job_id = upload(env['client']).json()['job_id']
    assert env['client'].get(f'/api/v1/analyze/{job_id}/result').status_code == 409


# The real queue: the API's enqueue sends the job as the Celery task, which a worker (run in-process here, eager mode)
# picks up and completes.
def test_celery_task_runs_the_job(env, monkeypatch):
    monkeypatch.setattr(tasks, 'analyze_video', lambda video, match_id, progress: json.loads(FIXTURE.read_text()))
    monkeypatch.setattr(tasks, 'get_store', lambda: env['store'])
    tasks.celery_app.conf.task_always_eager = True
    try:
        job_id = env['store'].create()
        path = env['dir'] / 'v.mp4'
        path.write_bytes(MP4_BYTES)
        tasks.enqueue(job_id, str(path))
        assert env['store'].get(job_id)['status'] == 'complete'
    finally:
        tasks.celery_app.conf.task_always_eager = False


# If the queue can't take the job (Redis down), the upload answers 503 with a sentence instead of a bare 500, the
# job is marked failed rather than left queued forever, and the saved video is deleted.
def test_queue_outage_fails_cleanly(env, monkeypatch):
    def broken(job_id, path):
        raise ConnectionError('redis down')

    monkeypatch.setattr(api, 'enqueue', broken)
    r = env['client'].post('/api/v1/analyze', files={'file': ('battle.mp4', MP4_BYTES, 'video/mp4')})
    assert r.status_code == 503
    assert 'try again' in r.json()['detail'].lower()
    assert list(env['dir'].iterdir()) == []
    [key] = env['redis'].keys('analysis:job:*')
    assert json.loads(env['redis'].get(key))['status'] == 'failed'
