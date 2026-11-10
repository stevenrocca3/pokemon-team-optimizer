"""Service settings, from the environment (deploy/docker-compose.yml sets them in production)."""
import os
import tempfile
from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class Settings:
    redis_url: str = field(default_factory=lambda: os.environ.get('REDIS_URL', 'redis://localhost:6379/0'))
    # shared by the API (writes uploads) and the worker (reads, then deletes them)
    upload_dir: Path = field(default_factory=lambda: Path(os.environ.get('UPLOAD_DIR', Path(tempfile.gettempdir()) / 'dexpresso-uploads')))
    max_upload_bytes: int = field(default_factory=lambda: int(os.environ.get('MAX_UPLOAD_MB', '400')) * 1024 * 1024)
    uploads_per_hour: int = field(default_factory=lambda: int(os.environ.get('UPLOADS_PER_HOUR', '6')))
    job_ttl_s: int = field(default_factory=lambda: int(os.environ.get('JOB_TTL_HOURS', '24')) * 3600)


settings = Settings()
