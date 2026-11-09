# Deploying DexPresso to dexpresso.app

Production is one Oracle Cloud VM (Always Free, Ampere ARM) running three containers from `deploy/`:

- **app**: the Docker image from the repo's `Dockerfile` (React app + Spring Boot on one origin). Flyway
  creates and migrates the database on startup.
- **db**: Postgres 17. Not reachable from the internet, only from the app.
- **caddy**: the only container with open ports (80, 443). It gets and renews the HTTPS certificate for
  `dexpresso.app` by itself. `.app` domains only work over HTTPS, so this part isn't optional.

Checked 2026-11-09: the stack builds and starts on ARM, a fresh database migrates V1 → V6, `backup.sh`
dumps restore cleanly, and Caddy validates the `Caddyfile`. Oracle console steps follow Oracle's docs as of
the same date; if a menu is named differently in your console, the field names below still match.

---

## 1. Create the VM

Oracle console → **navigation menu** → **Compute** → **Instances** → **Create instance**.

**Basic information**
- **Name:** `dexpresso`
- **Image and shape → Change image:** pick **Ubuntu**, then **Canonical Ubuntu 24.04** (the regular one,
  not *Minimal*; Oracle says to use the full Ubuntu image on Arm shapes) → **Select image**.
- **Image and shape → Change shape:** **Virtual machine** → **Ampere** → `VM.Standard.A1.Flex`.
  Set **Number of OCPUs: 2** and **Amount of memory (GB): 12**. That is the whole Always Free allowance
  for Ampere (more is billed on your account) → **Select shape**.

**Security:** leave the defaults.

**Networking:** keep the default VCN and public subnet it creates, and make sure
**Automatically assign public IPv4 address** is on.

**SSH keys:** choose **Generate a key pair for me** and click **Download private key** (save it as
`~/.ssh/dexpresso.key`), or paste your own public key (`cat ~/.ssh/id_ed25519.pub`).

**Storage:** the default boot volume (about 50 GB) is plenty and inside the free 200 GB.

Click **Create**. If it fails with *Out of host capacity*, pick another availability domain under
Placement, or try again later; Ampere capacity comes and goes.

When it's **Running**, copy the **Public IPv4 address** from the instance page.

> **Keeping the IP:** the address assigned at creation stays with the instance while it exists, including
> stops and reboots. If you'd rather have one that survives deleting and recreating the VM: Networking →
> IP management → Reserved public IPs → **Reserve public IP address**; then instance → **Networking** tab →
> Attached VNICs → the VNIC → **IP administration** tab → ⋯ on the private IP → **Edit** → Public IP type
> **No public IP** → **Update**, then **Edit** again → **Reserved public IP** → pick it → **Update**.
> Check the price on your account first; optional.

## 2. Open ports 80 and 443 (two firewalls, both needed)

**a) Oracle's network firewall.** Navigation menu → **Networking** → **Virtual cloud networks** → your VCN
→ **Security Lists** (under Resources, or a *Security* tab in newer consoles) → **Default Security List
for …** → **Add Ingress Rules**. Add these three rules (leave **Stateless** unchecked):

| Source Type | Source CIDR | IP Protocol | Source Port Range | Destination Port Range |
|---|---|---|---|---|
| CIDR | `0.0.0.0/0` | TCP | All | `80` |
| CIDR | `0.0.0.0/0` | TCP | All | `443` |
| CIDR | `0.0.0.0/0` | UDP | All | `443` |

**b) The VM's own firewall.** Oracle's Ubuntu image only allows SSH. Oracle says **not** to use `ufw` on
these images (it can stop the VM booting); edit `/etc/iptables/rules.v4` instead. SSH in:

```
ssh -i ~/.ssh/dexpresso.key ubuntu@<public-ip>      # chmod 600 the key first if ssh complains
```

then add the two rules right after the existing SSH rule, and load them:

```
sudo cp /etc/iptables/rules.v4 /etc/iptables/rules.v4.bak
sudo sed -i '/--dport 22 -j ACCEPT/a -A INPUT -p tcp -m state --state NEW -m multiport --dports 80,443 -j ACCEPT\n-A INPUT -p udp -m udp --dport 443 -j ACCEPT' /etc/iptables/rules.v4
sudo iptables-restore < /etc/iptables/rules.v4
sudo iptables -L INPUT -n --line-numbers | head -12   # 80,443 must be listed ABOVE the REJECT line
```

## 3. Install Docker and the app (still on the VM)

```
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit                                   # log out and SSH back in so the docker group applies
```

```
git clone https://github.com/stevenrocca3/pokemon-team-optimizer.git
cd pokemon-team-optimizer/deploy
cp .env.example .env && chmod 600 .env
openssl rand -base64 24                # paste this as DB_PASSWORD
nano .env                              # DB_PASSWORD, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
```

The Google client ID and secret are the same ones in your local `pokemon-optimizer-backend/.env`.

## 4. Point dexpresso.app at the VM (Cloudflare)

Cloudflare dashboard → **dexpresso.app** → **DNS** → **Records** → **Add record**, twice:

| Type | Name | IPv4 address | Proxy status |
|---|---|---|---|
| A | `@` | the VM's public IP | **DNS only** (grey cloud) |
| A | `www` | the VM's public IP | **DNS only** (grey cloud) |

Keep the cloud grey. With Cloudflare's proxy on (orange), Caddy can't get its certificate the simple way
and every visitor would look like the same Cloudflare IP to the rate limiter.

Check it: `dig +short dexpresso.app` on your Mac should print the VM's IP (usually within minutes).

## 5. Start it

On the VM, in `pokemon-team-optimizer/deploy`:

```
docker compose up -d --build           # first build: ~10+ minutes on 2 Ampere cores
docker compose logs -f caddy           # wait for "certificate obtained successfully", then Ctrl+C
docker compose ps                      # app, db and caddy all "Up"
```

Open https://dexpresso.app. If it doesn't load:
- `curl -I http://<public-ip>` from your Mac times out → a firewall from step 2 is still closed.
- Caddy logs say the certificate challenge failed → DNS isn't pointing at the VM yet, or port 80 is closed.
- The page loads but errors → `docker compose logs app`.

## 6. Google login

Google Cloud console → **APIs & Services**:
- **Credentials** → your OAuth client → **Authorized redirect URIs** → add
  `https://dexpresso.app/login/oauth2/code/google` → Save.
- **OAuth consent screen** (now called *Google Auth Platform* → *Branding* / *Audience* in newer consoles):
  authorized domain `dexpresso.app`, privacy policy `https://dexpresso.app/privacy`, terms
  `https://dexpresso.app/terms`. Then under **Audience**, **Publish app** so anyone can log in, not just
  your test users. Basic login scopes (name, email) don't need Google's review.

## 7. After launch

**Nightly backups.** On the VM, `crontab -e` and add:
```
30 3 * * * /home/ubuntu/pokemon-team-optimizer/deploy/backup.sh >> /home/ubuntu/backup.log 2>&1
```
Dumps go to `deploy/backups/`, 14 days kept. They're on the same VM, so copy one to your Mac now and then:
```
scp -i ~/.ssh/dexpresso.key ubuntu@<public-ip>:pokemon-team-optimizer/deploy/backups/<file>.dump .
```

**Restore test** (do once; never restore over the live database). In `deploy/` on the VM:
```
./backup.sh
docker compose exec -T db sh -c 'createdb -U "$POSTGRES_USER" restore_check'
docker compose exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d restore_check --no-owner' < backups/dexpresso-$(date +%F).dump
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d restore_check -c "select count(*) from caught_pokemon"'
docker compose exec -T db sh -c 'dropdb -U "$POSTGRES_USER" restore_check'
```

**Uptime monitoring.** A free monitor (UptimeRobot, Better Stack) on
`https://dexpresso.app/actuator/health`, every 5 minutes. It returns `{"status":"UP"}` only when the app
and the database are both fine.

**Shipping a new version.** On the VM: `cd ~/pokemon-team-optimizer/deploy && git pull && docker compose up -d --build`.

**Reading feedback and ratings.**
```
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d dexpresso'
SELECT created_at, page, message FROM feedback ORDER BY created_at DESC;
SELECT stars, count(*) FROM rating GROUP BY stars ORDER BY stars DESC;
```

**Error reports.** A player who hits a bug sees an id like `3fa9c2d1`; find the stack trace with
`docker compose logs app | grep 3fa9c2d1`.

Ubuntu installs security updates by itself; `sudo reboot` now and then for kernel updates. The containers
start again on their own.

---

## Settings

`deploy/.env` (copied from `.env.example`):

| Variable | What |
|---|---|
| `DB_USER`, `DB_PASSWORD` | Postgres credentials; the database is created with these on first start |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | from Google Cloud console → Credentials |
| `VITE_GOATCOUNTER_URL` | optional cookieless analytics, e.g. `https://dexpresso.goatcounter.com/count`; empty = off |

Already set in `docker-compose.yml`: the database URL, and `APP_CLIENT_IP_HEADER=X-Real-IP` (Caddy puts each
visitor's real IP there for the per-IP rate limits).

Team Builder tuning, if needed (add under `app: environment:` in `docker-compose.yml`):
`APP_TEAM_BUILDER_MAX_CONCURRENT` (default 2 searches at once; each already uses every core) and
`APP_TEAM_BUILDER_QUEUE_WAIT` (default `30s` before answering 503). On 2 OCPUs a search takes noticeably
longer than on a laptop; if players see many "busy" messages, try `MAX_CONCURRENT=1` with a `60s` wait.

## Running the production image on your Mac

The same stack, minus Caddy, with a throwaway database (port 8095):

```
cd deploy
cp .env.example .env   # fill in any DB_PASSWORD; Google values only matter for logging in
printf 'services:\n  app:\n    ports: ["8095:8080"]\n' > compose.local.yml
docker compose -p dexpresso-local -f docker-compose.yml -f compose.local.yml up -d --build db app
open http://localhost:8095
docker compose -p dexpresso-local down -v && rm compose.local.yml
```
