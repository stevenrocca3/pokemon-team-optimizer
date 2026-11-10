"""Issue #102: live progress pushed to the browser over Server-Sent Events."""
import json
import threading
import time

import fakeredis
import pytest
from fastapi.testclient import TestClient

from battle_analysis.service import api
from battle_analysis.service.store import JobStore


@pytest.fixture
def env(monkeypatch):
    """The API on an in-memory Redis, with a short heartbeat so the tests don't wait."""
    store = JobStore(fakeredis.FakeRedis())
    monkeypatch.setattr(api, 'get_store', lambda: store)
    monkeypatch.setattr(api.settings, 'heartbeat_s', 0.05)
    return {'client': TestClient(api.app), 'store': store}


def events(body: str) -> list[dict]:
    """The `data:` payloads of a text/event-stream body, in order."""
    return [json.loads(line[5:]) for line in body.splitlines() if line.startswith('data:')]


def later(delay: float, *steps):
    """Runs each step (a no-argument function) after `delay` seconds apart, on another thread, like a worker would."""
    def run():
        for step in steps:
            time.sleep(delay)
            step()
    t = threading.Thread(target=run, daemon=True)
    t.start()
    return t


# The stream is a real event stream: text/event-stream, never cached, and not buffered by a proxy (X-Accel-Buffering).
def test_stream_headers(env):
    job_id = env['store'].create()
    env['store'].update(job_id, status='complete', progress=100)
    r = env['client'].get(f'/api/v1/analyze/{job_id}/events')
    assert r.headers['content-type'].startswith('text/event-stream')
    assert r.headers['cache-control'] == 'no-cache'
    assert r.headers['x-accel-buffering'] == 'no'


# A job that's already finished sends its final status once and the stream closes (a page opened after the job ended).
def test_finished_job_sends_one_event_and_closes(env):
    job_id = env['store'].create()
    env['store'].update(job_id, status='complete', progress=100, score=88)
    got = events(env['client'].get(f'/api/v1/analyze/{job_id}/events').text)
    assert [(e['status'], e['progress'], e['score']) for e in got] == [('complete', 100, 88)]


# A running job streams every stage as the worker reaches it, starting from where it is now: queued, then
# processing_cv at 30%, auditing_turns, complete. Each event carries the status line the visualizer shows, and the
# stream closes after complete.
def test_running_job_streams_each_stage_in_order(env):
    store = env['store']
    job_id = store.create()
    later(0.1, lambda: store.update(job_id, status='processing_cv', progress=30),
          lambda: store.update(job_id, status='auditing_turns', progress=92),
          lambda: store.update(job_id, status='complete', progress=100, score=61))
    got = events(env['client'].get(f'/api/v1/analyze/{job_id}/events').text)
    assert [e['status'] for e in got] == ['queued', 'processing_cv', 'auditing_turns', 'complete']
    assert got[1]['progress'] == 30 and got[1]['message'] == 'Detecting Pokémon alignments...'
    assert got[-1]['score'] == 61


# A failure ends the stream too, with the reason for the player.
def test_failure_ends_the_stream_with_the_reason(env):
    store = env['store']
    job_id = store.create()
    later(0.1, lambda: store.fail(job_id, 'No battle HUD found in this video.'))
    got = events(env['client'].get(f'/api/v1/analyze/{job_id}/events').text)
    assert got[-1]['status'] == 'failed' and got[-1]['error'] == 'No battle HUD found in this video.'


# While nothing changes (a long CV pass) the stream sends heartbeat comments, so proxies and browsers keep it open.
def test_quiet_stretches_send_heartbeats(env):
    store = env['store']
    job_id = store.create()
    later(0.3, lambda: store.update(job_id, status='complete', progress=100))
    body = env['client'].get(f'/api/v1/analyze/{job_id}/events').text
    assert body.count(': ping') >= 2
    assert body.index(': ping') < body.index('"complete"')


# An unknown job is a 404, not a stream that waits forever.
def test_unknown_job_is_404(env):
    assert env['client'].get('/api/v1/analyze/' + 'e' * 32 + '/events').status_code == 404
