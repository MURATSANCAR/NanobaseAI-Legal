#!/usr/bin/env bash
# Deploy SpecAI Legal to portal.nanobase.ai and wire the hub "Legal" card → /legal/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVER="${SERVER:-nanobase}"
REMOTE_DIR="${REMOTE_DIR:-/data/nanobaseai/legal}"

echo "==> Syncing to ${SERVER}:${REMOTE_DIR}"
ssh "${SERVER}" "sudo mkdir -p '${REMOTE_DIR}' && sudo chown \"\$(whoami):\" '${REMOTE_DIR}'"
rsync -az --delete \
  --exclude '.git' \
  --exclude 'frontend/node_modules' \
  --exclude 'frontend/.next' \
  --exclude 'target' \
  --exclude '.pytest_cache' \
  --exclude '.wrangler' \
  "${ROOT}/" "${SERVER}:${REMOTE_DIR}/"

echo "==> Remote install (compose + systemd + nginx + hub patch)"
ssh "${SERVER}" bash -s <<'REMOTE'
set -euo pipefail
DIR=/data/nanobaseai/legal
cd "$DIR"

# Run install (env, db, minio, compose up)
sudo bash deploy/easymeeting/install.sh

# Wire nginx /legal/ + /legal-api/ + /contracts → /legal/ (idempotent).
# NOTE: portal.nanobase.ai is a file under sites-enabled (not always a symlink).
CONF=/etc/nginx/sites-enabled/portal.nanobase.ai
CONF_AVAIL=/etc/nginx/sites-available/portal.nanobase.ai
SNIPPET="$DIR/deploy/easymeeting/nginx-legal.conf"
sudo python3 - <<PY
from pathlib import Path
import re
conf = Path("$CONF")
avail = Path("$CONF_AVAIL")
snippet = Path("$SNIPPET").read_text()
text = conf.read_text()
# Ensure old /contracts UI always lands on SpecAI Legal
redirects = """
    # Old Contract Intelligence UI → SpecAI Legal
    location = /contracts {
        return 301 /legal/;
    }
    location = /contracts/ {
        return 301 /legal/;
    }
    location ^~ /contracts/ {
        return 301 /legal/;
    }

"""
if "location = /contracts {" not in text or "return 301 /legal/" not in text:
    if "    # SpecAI Legal" in text:
        text = text.replace("    # SpecAI Legal", redirects + "    # SpecAI Legal", 1)
    elif "    location /legal-api/" in text:
        text = text.replace("    location /legal-api/", redirects + "    location /legal-api/", 1)
    print("nginx /contracts → /legal redirects inserted")
else:
    print("nginx /contracts redirects already present")

if "location /legal/" in text and "upstream legal_api" in text:
    print("nginx /legal already configured")
else:
    # Drop stale partial inserts if any
    import re
    text = re.sub(r"\nupstream legal_api \{.*?\n\}\n", "\n", text, flags=re.S)
    text = re.sub(r"\nupstream legal_portal \{.*?\n\}\n", "\n", text, flags=re.S)
    text = re.sub(
        r"\n\s*# SpecAI Legal.*?location /legal/ \{.*?\n\s*\}\n",
        "\n",
        text,
        flags=re.S,
    )

    # Insert upstreams after first upstream block header area
    if "upstream legal_api" not in text:
        anchor = "upstream nanobase_superset {"
        if anchor not in text:
            raise SystemExit("Could not find upstream insertion point")
        ups = """
upstream legal_api {
    # SpecAI Legal backend (compose.easymeeting.yaml). Not 8089 — that is Superset.
    server 127.0.0.1:8098;
    keepalive 16;
}

upstream legal_portal {
    server 127.0.0.1:3020;
    keepalive 8;
}

"""
        text = text.replace(anchor, ups + anchor, 1)

    locations = """
    # SpecAI Legal — https://portal.nanobase.ai/legal/
    location /legal-api/ {
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_read_timeout 300s;
        client_max_body_size 100M;
        proxy_pass http://legal_api/;
    }

    location = /legal {
        return 301 /legal/;
    }

    location /legal/ {
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_pass http://legal_portal;
    }

"""
    needle = "    location /assets/ {"
    if needle not in text:
        needle = "    location / {"
    if needle not in text:
        raise SystemExit("Could not find location insertion point")
    if "location /legal/" not in text:
        text = text.replace(needle, locations + needle, 1)
    print("nginx /legal locations inserted")

conf.write_text(text)
if avail.exists() and avail.resolve() != conf.resolve():
    avail.write_text(text)
    print("synced sites-available from sites-enabled")
PY
sudo nginx -t
sudo systemctl reload nginx

# Point portal hub Legal card to /legal/ (same pattern as QA → /QA)
INDEX_JS=$(ls /data/nanobaseai-mobile/portal/dist/assets/index-*.js | head -1)
if [ -n "$INDEX_JS" ]; then
  sudo python3 - <<PY
from pathlib import Path
p = Path("$INDEX_JS")
t = p.read_text()
candidates = [
    ('{kind:"internal",to:"/contracts",buildModule:"contracts"',
     '{kind:"external",href:"/legal/",buildModule:"contracts"'),
    ('{kind:"internal",to:"/contracts/",buildModule:"contracts"',
     '{kind:"external",href:"/legal/",buildModule:"contracts"'),
    ('{kind:"external",href:"/contracts",buildModule:"contracts"',
     '{kind:"external",href:"/legal/",buildModule:"contracts"'),
    ('{kind:"external",href:"/contracts/",buildModule:"contracts"',
     '{kind:"external",href:"/legal/",buildModule:"contracts"'),
]
changed = False
for old, new in candidates:
    if new in t:
        print("portal landing already points Legal card to /legal/")
        changed = True
        break
    if old in t:
        p.write_text(t.replace(old, new, 1))
        print(f"patched {p.name}: Legal card -> /legal/")
        changed = True
        break
if not changed:
    print("WARNING: landing Legal card pattern not found; skip patch")
PY
  sudo python3 "$DIR/deploy/easymeeting/patch-hub-contracts-redirect.py" "$INDEX_JS"
else
  echo "WARNING: portal hub index-*.js not found"
fi

echo "==> Smoke"
curl -sS -o /dev/null -w "local portal %{http_code}\n" http://127.0.0.1:3020/legal/ || true
curl -sS -o /dev/null -w "local api %{http_code}\n" http://127.0.0.1:8098/actuator/health || true
curl -sS -o /dev/null -w "public legal %{http_code}\n" https://portal.nanobase.ai/legal/ || true
curl -sS -o /dev/null -w "public contracts -> %{redirect_url} (%{http_code})\n" https://portal.nanobase.ai/contracts || true
REMOTE

echo "==> Done. Open https://portal.nanobase.ai/ and click NanobaseAI - Legal"
echo "    Direct: https://portal.nanobase.ai/legal/"
echo "    Old /contracts redirects to /legal/"
