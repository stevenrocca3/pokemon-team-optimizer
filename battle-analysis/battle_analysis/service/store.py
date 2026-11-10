"""
Jobs in Redis: each job's status (a small JSON doc), its result once done, and a pub/sub channel that carries every
status change to whoever is listening (the SSE stream, #102). Everything expires after a day: results are kept in the
player's browser, not here.
"""
import json
import time
import uuid

STAGE_MESSAGES = {
    'queued': 'Waiting for a free worker...',
    'processing_cv': 'Detecting Pokémon alignments...',
    'reconstructing': 'Rebuilding the battle turn by turn...',
    'auditing_turns': 'Auditing fast move turns...',
    'complete': 'Analysis complete.',
    'failed': 'Analysis failed.',
}
FINAL = ('complete', 'failed')


class JobStore:
    def __init__(self, redis, ttl_s: int = 86400):
        self.redis = redis
        self.ttl_s = ttl_s

    @staticmethod
    def _key(job_id: str) -> str:
        return f'analysis:job:{job_id}'

    @staticmethod
    def channel(job_id: str) -> str:
        return f'analysis:events:{job_id}'

    def create(self) -> str:
        job_id = uuid.uuid4().hex
        now = time.time()
        self._save(job_id, {'job_id': job_id, 'status': 'queued', 'progress': 0, 'message': STAGE_MESSAGES['queued'],
                            'error': None, 'score': None, 'tier': None, 'created_at': now, 'updated_at': now})
        return job_id

    def get(self, job_id: str) -> dict | None:
        raw = self.redis.get(self._key(job_id))
        return json.loads(raw) if raw else None

    def update(self, job_id: str, **fields) -> dict:
        status = self.get(job_id) or {'job_id': job_id}
        if 'status' in fields and 'message' not in fields:
            fields['message'] = STAGE_MESSAGES.get(fields['status'], status.get('message'))
        status |= fields | {'updated_at': time.time()}
        self._save(job_id, status)
        return status

    def _save(self, job_id: str, status: dict) -> None:
        payload = json.dumps(status)
        self.redis.set(self._key(job_id), payload, ex=self.ttl_s)
        self.redis.publish(self.channel(job_id), payload)

    def set_result(self, job_id: str, result: dict) -> None:
        self.redis.set(f'analysis:result:{job_id}', json.dumps(result), ex=self.ttl_s)
        report = result['report']
        self.update(job_id, status='complete', progress=100, score=report['score'], tier=report['tier']['name'])

    def get_result(self, job_id: str) -> dict | None:
        raw = self.redis.get(f'analysis:result:{job_id}')
        return json.loads(raw) if raw else None

    def fail(self, job_id: str, error: str) -> None:
        self.update(job_id, status='failed', error=error)

    def allow(self, client: str, per_hour: int) -> bool:
        """Counts one attempt for this client this hour; False once it's over the limit."""
        key = f'analysis:rate:{client}:{int(time.time() // 3600)}'
        count = self.redis.incr(key)
        if count == 1:
            self.redis.expire(key, 3600)
        return count <= per_hour
