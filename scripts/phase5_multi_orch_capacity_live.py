#!/usr/bin/env python3
"""Phase 5: multi-orchestrator global capacity without invoking the model runtime."""
from __future__ import annotations

import json
import subprocess
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

REPORT = Path("/tmp/phase5_multi_orch_capacity_report.json")
PROFILE = "BALANCED"
PREFIX = f"specai:phase5-capacity:{uuid.uuid4().hex[:8]}"


def docker_py(container: str, code: str) -> str:
    return subprocess.check_output(
        ["sudo", "docker", "exec", "-i", container, "python", "-c", code],
        text=True,
    ).strip()


ACQUIRE_CODE = """
import json, os, time
from capacity import RedisModelCapacityManager
host=os.environ.get('REDIS_HOST','actenora-prodlike-redis')
password=os.environ.get('REDIS_PASSWORD','')
url=f'redis://:{password}@{host}:6379/0' if password else f'redis://{host}:6379/0'
mgr=RedisModelCapacityManager(url, key_prefix='__PREFIX__', default_lease_ttl_ms=15000, failure_policy='FAIL_CLOSED')
status, lease, wait=mgr.acquire(model_profile='__PROFILE__', max_concurrency=1, owner_id='__OWNER__', correlation_id='__CORR__', wait_timeout_seconds=0, lease_ttl_ms=15000)
print(json.dumps({'status':status,'leaseId':None if lease is None else lease.lease_id,'generation':None if lease is None else lease.generation,'waitMs':wait,'owner':'__OWNER__'}))
"""

RELEASE_CODE = """
import json, os
from capacity import RedisModelCapacityManager, CapacityLease
host=os.environ.get('REDIS_HOST','actenora-prodlike-redis')
password=os.environ.get('REDIS_PASSWORD','')
url=f'redis://:{password}@{host}:6379/0' if password else f'redis://{host}:6379/0'
mgr=RedisModelCapacityManager(url, key_prefix='__PREFIX__', default_lease_ttl_ms=15000, failure_policy='FAIL_CLOSED')
lease=CapacityLease(lease_id='__LEASE__', model_profile='__PROFILE__', owner_id='__OWNER__', generation=__GEN__, acquired_at_ms=0, expires_at_ms=0)
print(json.dumps({'status': mgr.release(lease)}))
"""

SNAPSHOT_CODE = """
import json, os
from capacity import RedisModelCapacityManager
host=os.environ.get('REDIS_HOST','actenora-prodlike-redis')
password=os.environ.get('REDIS_PASSWORD','')
url=f'redis://:{password}@{host}:6379/0' if password else f'redis://{host}:6379/0'
mgr=RedisModelCapacityManager(url, key_prefix='__PREFIX__', default_lease_ttl_ms=15000, failure_policy='FAIL_CLOSED')
print(json.dumps(mgr.snapshot('__PROFILE__')))
"""


def fill(template: str, **kwargs) -> str:
    out = template
    for key, value in kwargs.items():
        out = out.replace(f"__{key.upper()}__", str(value))
    return out


def main() -> int:
    # Discover container names
    names = subprocess.check_output(
        ["sudo", "docker", "ps", "--format", "{{.Names}}"], text=True
    ).splitlines()
    orch_a = next(n for n in names if "ai-orchestrator" in n and "orchestrator-b" not in n)
    orch_b = next(n for n in names if "ai-orchestrator-b" in n)

    ready_a = json.loads(
        subprocess.check_output(["curl", "-s", "http://127.0.0.1:8095/health/ready"], text=True)
    )
    ready_b = json.loads(
        subprocess.check_output(["curl", "-s", "http://127.0.0.1:8096/health/ready"], text=True)
    )

    barrier = threading.Barrier(2)
    results = []

    def race(container: str, owner: str):
        barrier.wait(timeout=30)
        code = fill(
            ACQUIRE_CODE,
            prefix=PREFIX,
            profile=PROFILE,
            owner=owner,
            corr=str(uuid.uuid4()),
        )
        return json.loads(docker_py(container, code))

    with ThreadPoolExecutor(max_workers=2) as pool:
        futs = [
            pool.submit(race, orch_a, "orchestrator-a"),
            pool.submit(race, orch_b, "orchestrator-b"),
        ]
        for fut in as_completed(futs):
            results.append(fut.result())

    snap = json.loads(
        docker_py(orch_a, fill(SNAPSHOT_CODE, prefix=PREFIX, profile=PROFILE))
    )
    acquired = [r for r in results if r["status"] == "ACQUIRED"]
    full = [r for r in results if r["status"] == "CAPACITY_FULL"]
    passed = len(acquired) == 1 and len(full) == 1 and int(snap.get("active", -1)) == 1

    # release winner then confirm second can acquire
    winner = acquired[0]
    rel = json.loads(
        docker_py(
            orch_a,
            fill(
                RELEASE_CODE,
                prefix=PREFIX,
                profile=PROFILE,
                lease=winner["leaseId"],
                owner=winner["owner"],
                gen=winner["generation"],
            ),
        )
    )
    second = json.loads(
        docker_py(
            orch_b,
            fill(
                ACQUIRE_CODE,
                prefix=PREFIX,
                profile=PROFILE,
                owner="orchestrator-b-followup",
                corr=str(uuid.uuid4()),
            ),
        )
    )
    if second.get("leaseId"):
        docker_py(
            orch_b,
            fill(
                RELEASE_CODE,
                prefix=PREFIX,
                profile=PROFILE,
                lease=second["leaseId"],
                owner="orchestrator-b-followup",
                gen=second["generation"],
            ),
        )

    passed = passed and rel["status"] == "RELEASED" and second["status"] == "ACQUIRED"
    report = {
        "orchestratorA": orch_a,
        "orchestratorB": orch_b,
        "readyA": ready_a,
        "readyB": ready_b,
        "prefix": PREFIX,
        "raceResults": results,
        "snapshotDuringHold": snap,
        "release": rel,
        "secondAcquireAfterRelease": second,
        "modelConcurrencyPeak": snap.get("active"),
        "result": "PASS" if passed else "FAIL",
    }
    REPORT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
