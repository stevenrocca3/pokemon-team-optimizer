"""Issue #105 (backend half): every audit is its own status line, so the live log on /analyze shows real stages."""
import json
from pathlib import Path

import fakeredis

from battle_analysis.analysis.scoring import analyze_match
from battle_analysis.service import tasks
from battle_analysis.service.store import JobStore

FIXTURE = Path(__file__).parent / 'fixtures/rec1_turn_log.json'
AUDIT_LINES = [
    'Auditing fast move turns...',
    'Evaluating shield decisions...',
    'Checking switch timing...',
    'Reviewing charged move choices...',
    'Scoring against the Legend benchmark...',
]


# analyze_match announces each audit before running it, in the order they run, and announcing changes nothing in
# the report (rec 1 still scores 88 with the same findings).
def test_analyze_match_announces_each_audit():
    log = json.loads(FIXTURE.read_text())
    heard = []
    report = analyze_match(log, progress=heard.append)
    assert heard == AUDIT_LINES
    assert report == analyze_match(log)


# The worker publishes those lines as the auditing_turns stage, progress rising past the CV pass's 90% on each, and
# then complete: what the /analyze log feed receives over the event stream.
def test_worker_publishes_each_audit_stage(tmp_path, monkeypatch):
    redis = fakeredis.FakeRedis()
    store = JobStore(redis)
    job_id = store.create()
    video = tmp_path / 'v.mp4'
    video.write_bytes(b'x')
    monkeypatch.setattr(tasks, 'analyze_video', lambda path, match_id, progress: json.loads(FIXTURE.read_text()))
    pubsub = redis.pubsub()
    pubsub.subscribe(store.channel(job_id))
    tasks.run_job(job_id, str(video), store=store)

    # (the subscribe confirmation reads as None, so drain a fixed number of reads rather than stop at the first None)
    reads = [pubsub.get_message(ignore_subscribe_messages=True) for _ in range(100)]
    published = [json.loads(m['data']) for m in reads if m]
    auditing = [p for p in published if p['status'] == 'auditing_turns']
    assert [p['message'] for p in auditing] == AUDIT_LINES
    progress = [p['progress'] for p in auditing]
    assert progress == sorted(progress) and progress[0] > 90 and progress[-1] < 100
    assert published[-1]['status'] == 'complete'
