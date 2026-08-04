# Image rebuild — hot-patch → kalıcı image

**Süre:** ~10 dk (pip cache hit ise)  
**Host:** `nanobase` · proje `specai-legal` · kod `/data/nanobaseai/legal`

## 1) Kaynakları senkronla

Host `services/document-intelligence/` içinde şunlar olmalı:

- `markdown_clause_parser.py`
- `requirement_from_clauses.py`
- `error_to_state.py`
- `reprocess_policy.py`
- `markdown_short_circuit.py`
- `bounded_parser.py`
- `app.py`
- `Dockerfile` (COPY listesinde core modüller)

## 2) Build + recreate (yalnız DI)

```bash
cd /data/nanobaseai/legal
sudo docker compose -p specai-legal --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml -f compose.easymeeting.yaml \
  build document-intelligence

sudo docker compose -p specai-legal --env-file /etc/nanobaseai/legal.env \
  -f compose.yaml -f compose.easymeeting.yaml \
  up -d --force-recreate --no-deps document-intelligence
```

Healthy olana kadar:

```bash
sudo docker inspect -f '{{.State.Health.Status}}' specai-legal-document-intelligence-1
```

## 3) Baked doğrulama

```bash
sudo docker exec specai-legal-document-intelligence-1 python -c "
from pathlib import Path
for n in ['markdown_clause_parser.py','requirement_from_clauses.py','error_to_state.py','reprocess_policy.py']:
    assert (Path('/app')/n).exists(), n
import app
assert 'forceMode' in app.ParseRequest.model_fields
print('baked_ok')
"
```

## 4) Worker scale (opsiyonel, yüksek concurrency için)

`compose.easymeeting.yaml` → `document-intelligence`:

```yaml
environment:
  WORKER_THREADS: "2"
```

ve CMD tarafında uvicorn workers için image override / command:

```yaml
command: ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "2", "--no-access-log"]
```

Sonra recreate. Not: workers>1 ile in-memory metrics/job sqlite contention artabilir; soak önce `workers=2` ile doğrula.

## Rollback image

Önceki digest’e dön:

```bash
sudo docker tag <previous-digest> specai-legal-document-intelligence:latest
# recreate as above
```
