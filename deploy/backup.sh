#!/usr/bin/env bash
# Nightly Postgres backup, run by cron on the server (see docs/DEPLOY.md). Keeps the last 14 days.
# Custom format (-Fc) so pg_restore can restore it into a fresh database.
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p backups
file="backups/dexpresso-$(date +%F).dump"

docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d dexpresso -Fc' > "$file.partial"
mv "$file.partial" "$file"   # only a finished dump gets the real name

find backups -name 'dexpresso-*.dump' -mtime +14 -delete
echo "$(date "+%F %T") backed up $(du -h "$file" | cut -f1) to $file"
