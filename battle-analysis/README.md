# Battle analysis

Reads a GO Battle League screen recording, rebuilds the battle turn by turn (`battle_analysis/cv`), audits it and
scores it against the Legend benchmark (`battle_analysis/analysis`). `battle_analysis/service` runs it behind an API:
uploads go on a Redis queue, a Celery worker reads them, and progress streams to the browser over Server-Sent Events.

## Develop

```sh
cd battle-analysis
python3.13 -m venv .venv && .venv/bin/pip install -r requirements.txt -r requirements-dev.txt
brew install tesseract redis
.venv/bin/python -m pytest tests -m "not recordings"   # add the recordings test (~90 s) by dropping -m
```

Run the service next to the frontend's dev server (Vite proxies `/api/v1/analyze` to port 8090):

```sh
redis-server
.venv/bin/uvicorn battle_analysis.service.api:app --port 8090 --reload
.venv/bin/celery -A battle_analysis.service.tasks worker --concurrency 1 --loglevel INFO
```

## Production

`deploy/docker-compose.yml` runs it as `analysis-api` and `analysis-worker` (one image, `battle-analysis/Dockerfile`,
built from the repo root) with `redis`, and Caddy routes `/api/v1/analyze*` to the API. Settings, all optional:
`MAX_UPLOAD_MB` (400), `UPLOADS_PER_HOUR` per IP (6), `JOB_TTL_HOURS` (24), `SSE_HEARTBEAT_S` (15).
Videos are deleted as soon as they're read; job status and results expire after `JOB_TTL_HOURS`.
