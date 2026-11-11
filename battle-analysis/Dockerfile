# The battle analysis service: one image, two roles (see deploy/docker-compose.yml).
#   analysis-api     uvicorn battle_analysis.service.api:app   takes uploads, reports status, streams progress
#   analysis-worker  celery -A battle_analysis.service.tasks   reads the videos, one at a time
# Build from the repo root (it needs the backend's gamemaster):  docker build -f battle-analysis/Dockerfile .

FROM python:3.13-slim
# Tesseract reads the HUD text; OpenCV's headless wheel still wants glib
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY battle-analysis/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
# the same gamemaster and movesets the Spring app battles with
COPY pokemon-optimizer-backend/src/main/resources/gamemaster.json gamedata/
COPY pokemon-optimizer-backend/src/main/resources/meta/movesets.json gamedata/meta/
COPY battle-analysis/battle_analysis ./battle_analysis
ENV GAMEDATA_DIR=/srv/gamedata \
    UPLOAD_DIR=/uploads \
    PYTHONUNBUFFERED=1
RUN useradd --create-home analysis && mkdir -p /uploads && chown analysis /uploads
USER analysis
EXPOSE 8090
CMD ["uvicorn", "battle_analysis.service.api:app", "--host", "0.0.0.0", "--port", "8090"]
