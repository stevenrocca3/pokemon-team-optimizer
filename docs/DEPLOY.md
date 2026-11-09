# Deploying DexPresso

The whole site is one Docker image (`Dockerfile` at the repo root): the React app is built and copied into
Spring's `static/`, so the site and the API share one origin (no CORS, one cookie domain). Flyway creates
and migrates the schema on startup (`resources/db/migration`), and Hibernate only validates it.

## What the host needs

- Something that builds a Dockerfile from GitHub and runs it without sleeping: a sleeping server makes the
  first visitor wait for Spring to start. The Team Builder is CPU-heavy, so more cores = faster builds.
- A Postgres database (17 locally; 15+ is fine).
- HTTPS in front (every managed host does this).

## Environment variables

| Variable | Example |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://<host>:5432/<db>` (JDBC form, not `postgres://`) |
| `DB_USER`, `DB_PASSWORD` | the database's credentials |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | from the Google Cloud console |
| `PORT` | set by most hosts; defaults to 8080 |
| `APP_CLIENT_IP_HEADER` | the header your host puts the visitor's real IP in, e.g. `Fly-Client-IP` or `CF-Connecting-IP`. Rate limits are per IP, so without it every visitor shares the proxy's IP and one limit |

Optional tuning (defaults in brackets): `APP_TEAM_BUILDER_MAX_CONCURRENT` [2] searches at once, each already
using every core; `APP_TEAM_BUILDER_QUEUE_WAIT` [30s] before a 503.

Optional analytics: build with `--build-arg VITE_GOATCOUNTER_URL=https://<code>.goatcounter.com/count`
(cookieless, so no consent banner). Without it, no analytics script loads.

`SPRING_PROFILES_ACTIVE=prod` is already set in the image (`application-prod.properties`).

## Google login

In Google Cloud console → APIs & Services → Credentials → the OAuth client, add the deployed redirect URI:
`https://<your-domain>/login/oauth2/code/google`. While the consent screen is in Testing mode, only listed
test users can log in; publishing it needs the privacy policy (LAUNCH_CHECKLIST #1).

## Try the production image locally

```
docker build -t dexpresso .
docker run --rm -p 8090:8080 --env-file pokemon-optimizer-backend/.env \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/dexpresso dexpresso
```
Then open http://localhost:8090. (Google login won't work on 8090 unless that redirect URI is registered.)

## Reading feedback

`SELECT created_at, page, message FROM feedback ORDER BY created_at DESC;`

## Uptime monitoring

Point a free monitor (UptimeRobot, Better Stack, ...) at `https://<your-domain>/actuator/health` every 5 minutes.
It answers `{"status":"UP"}` only when the app and its database are both fine; every other actuator endpoint is
off. Unexpected errors are logged as `Unhandled error <id>`, and that id is what a player sees, so a report like
"error 3fa9c2d1" finds its stack trace in the host's logs.

## Backups

Most managed Postgres hosts take daily backups; turn them on and check the retention. To take your own:

```
pg_dump "postgresql://<user>:<password>@<host>:5432/<db>" -Fc -f dexpresso-$(date +%F).dump
```

Restore into a fresh database (never straight over the live one), check it, then point the app at it:

```
createdb restore_check
pg_restore -d restore_check --no-owner dexpresso-2026-11-08.dump
psql -d restore_check -c "select count(*) from caught_pokemon"
```

Tested 2026-11-08 against the local database: every table's row count, the Flyway history and
`caught_pokemon_seq` came back identical. Repeat the restore once on the real host before launch.
