#!/usr/bin/env python3
import json, time, urllib.request, subprocess, uuid

ORG = "11111111-1111-1111-1111-111111111111"
PROJECT = "76c32181-c0c5-4e6f-90df-d90d3f38845c"
API = "http://127.0.0.1:8098"


def env(k):
    return subprocess.check_output(
        ["sudo", "grep", f"^{k}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def api(method, path, token, body=None, timeout=30):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(
        API + path,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-Organization-Id": ORG,
            "X-Correlation-ID": str(uuid.uuid4()),
        },
    )
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        return resp.status, (json.loads(raw) if raw else None), int((time.time() - t0) * 1000)


data = json.dumps(
    {
        "email": env("SPECAI_LOCAL_ADMIN_EMAIL"),
        "password": env("SPECAI_LOCAL_ADMIN_PASSWORD"),
    }
).encode()
req = urllib.request.Request(
    API + "/api/v1/auth/login",
    data=data,
    method="POST",
    headers={"Content-Type": "application/json"},
)
with urllib.request.urlopen(req, timeout=60) as resp:
    token = json.loads(resp.read())["accessToken"]

st, job, _ = api("POST", f"/api/v1/tenders/{PROJECT}/compliance-analyses", token, {})
job_id = job["id"]
print("CREATED", job_id, job.get("status"), flush=True)

running = False
status = None
for _ in range(40):
    st, snap, lat = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
    status = snap.get("status")
    print(f"POLL {status} getLatencyMs={lat}", flush=True)
    if status == "RUNNING":
        running = True
        break
    if status in {"COMPLETED", "FAILED", "CANCELLED", "PARTIALLY_COMPLETED"}:
        break
    time.sleep(1)

if not running:
    print("NO_RUNNING", status, flush=True)
    raise SystemExit(1)

st, cancel, cancel_ms = api("POST", f"/api/v1/compliance-analyses/{job_id}/cancel", token, {})
print(
    "CANCEL",
    st,
    "cancelLatencyMs=",
    cancel_ms,
    "status=",
    cancel.get("status"),
    "cancel_requested_at=",
    cancel.get("cancel_requested_at"),
    flush=True,
)

for _ in range(90):
    st, snap, lat = api("GET", f"/api/v1/compliance-analyses/{job_id}", token)
    print(
        f"AFTER {snap.get('status')} getLatencyMs={lat} "
        f"cancel_requested_at={snap.get('cancel_requested_at')}",
        flush=True,
    )
    if snap.get("status") in {"CANCELLED", "COMPLETED", "FAILED", "PARTIALLY_COMPLETED"}:
        print(
            "FINAL",
            json.dumps(
                {
                    "status": snap.get("status"),
                    "cancelLatencyMs": cancel_ms,
                    "cancel_requested_at": str(snap.get("cancel_requested_at")),
                    "started_at": str(snap.get("started_at")),
                    "completed_at": str(snap.get("completed_at")),
                    "pass": snap.get("status") == "CANCELLED" and cancel_ms < 5000,
                }
            ),
            flush=True,
        )
        break
    time.sleep(2)
else:
    raise SystemExit("timeout waiting terminal after cancel")
