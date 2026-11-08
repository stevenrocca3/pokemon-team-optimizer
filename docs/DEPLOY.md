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
