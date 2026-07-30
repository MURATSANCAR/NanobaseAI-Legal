#!/usr/bin/env python3
"""Phase 5: capacity lease TTL recovery after owner disappears (no release)."""
from __future__ import annotations

import json
import subprocess
import time
import uuid
from pathlib import Path

REPORT = Path("/tmp/phase5_orchestrator_crash_capacity_report.json")
PREFIX = f"specai:phase5-orch-crash:{uuid.uuid4().hex[:8]}"
PROFILE = "BALANCED"


def docker_py(container: str, code: str) -> str:
    return subprocess.check_output(
        ["sudo", "docker", "exec", "-i", container, "python", "-c", code],
        text=True,
    ).strip()


def main() -> int:
    names = subprocess.check_output(
        ["sudo", "docker", "ps", "--format", "{{.Names}}"], text=True
    ).splitlines()
    orch_a = next(n for n in names if "ai-orchestrator" in n and "orchestrator-b" not in n)
    orch_b = next(n for n in names if "ai-orchestrator-b" in n)

    acquire = f"""
import json, os
from capacity import RedisModelCapacityManager
host=os.environ.get('REDIS_HOST','actenora-prodlike-redis')
password=os.environ.get('REDIS_PASSWORD','')
url=f'redis://:{{password}}@{{host}}:6379/0' if password else f'redis://{{host}}:6379/0'
mgr=RedisModelCapacityManager(url, key_prefix='{PREFIX}', default_lease_ttl_ms=2000, failure_policy='FAIL_CLOSED')
status, lease, wait=mgr.acquire(model_profile='{PROFILE}', max_concurrency=1, owner_id='orchestrator-a-crash', correlation_id='c1', wait_timeout_seconds=0, lease_ttl_ms=2000)
print(json.dumps({{'status':status,'leaseId':None if lease is None else lease.lease_id,'active':mgr.snapshot('{PROFILE}').get('active')}}))
"""
    before = json.loads(docker_py(orch_a, acquire))
    killed_at = time.time()
    # Do NOT release — simulate crash.
    time.sleep(2.5)
    expire_check = f"""
import json, os
from capacity import RedisModelCapacityManager
host=os.environ.get('REDIS_HOST','actenora-prodlike-redis')
password=os.environ.get('REDIS_PASSWORD','')
url=f'redis://:{{password}}@{{host}}:6379/0' if password else f'redis://{{host}}:6379/0'
mgr=RedisModelCapacityManager(url, key_prefix='{PREFIX}', default_lease_ttl_ms=2000, failure_policy='FAIL_CLOSED')
snap=mgr.snapshot('{PROFILE}')
status, lease, wait=mgr.acquire(model_profile='{PROFILE}', max_concurrency=1, owner_id='orchestrator-b-recovery', correlation_id='c2', wait_timeout_seconds=0, lease_ttl_ms=5000)
if lease is not None:
    mgr.release(lease)
print(json.dumps({{'snapActive':snap.get('active'),'status':status,'leaseId':None if lease is None else lease.lease_id}}))
"""
    after = json.loads(docker_py(orch_b, expire_check))
    passed = (
        before["status"] == "ACQUIRED"
        and before["active"] == 1
        and after["snapActive"] == 0
        and after["status"] == "ACQUIRED"
        and after["leaseId"] != before["leaseId"]
    )
    report = {
        "capacityLeaseIdBefore": before.get("leaseId"),
        "capacityLeaseIdAfter": after.get("leaseId"),
        "workerKilledAt": killed_at,
        "activeBefore": before.get("active"),
        "activeAfterTtl": after.get("snapActive"),
        "before": before,
        "after": after,
        "result": "PASS" if passed else "FAIL",
    }
    REPORT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
