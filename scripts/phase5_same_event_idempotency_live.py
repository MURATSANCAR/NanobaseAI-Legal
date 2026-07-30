#!/usr/bin/env python3
"""Phase 5: same-event idempotency — dual claim on processed_message for one eventId."""
from __future__ import annotations

import json
import subprocess
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
CONSUMER = "compliance-analysis-consumer-v1"
REPORT = Path("/tmp/phase5_same_event_idempotency_report.json")


def env(key: str) -> str:
    return subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    ).strip().split("=", 1)[1]


def psql(sql: str) -> str:
    path = f"/tmp/se_{uuid.uuid4().hex}.sql"
    Path(path).write_text(sql)
    name = Path(path).name
    subprocess.check_call(["sudo", "docker", "cp", path, f"actenora-prodlike-postgres:/tmp/{name}"])
    return subprocess.check_output(
        [
            "sudo",
            "docker",
            "exec",
            "-e",
            f"PGPASSWORD={env('DATABASE_PASSWORD')}",
            "actenora-prodlike-postgres",
            "psql",
            "-U",
            env("DATABASE_USER"),
            "-d",
            "specai",
            "-v",
            "ON_ERROR_STOP=1",
            "-At",
            "-f",
            f"/tmp/{name}",
        ],
        text=True,
    ).strip()


def claim(event_id: str, worker: str, barrier: threading.Barrier) -> dict:
    barrier.wait(timeout=30)
    started = time.time()
    row_id = str(uuid.uuid4())
    # Exact ConsumerIdempotencyService / ProcessedMessageRepository.claim semantics.
    sql = f"""
with claimed as (
  insert into processed_message (
      id, consumer_name, event_id, processed_at, result_status
  ) values (
      '{row_id}', '{CONSUMER}', '{event_id}', now(), 'PROCESSING'
  )
  on conflict (consumer_name, event_id) do update
  set id = excluded.id,
      processed_at = excluded.processed_at,
      result_status = 'PROCESSING'
  where processed_message.result_status = 'FAILED'
     or (
         processed_message.result_status = 'PROCESSING'
         and processed_message.processed_at <= now() - interval '30 minutes'
     )
  returning id
)
select count(*)::text from claimed;
"""
    out = psql(sql).strip()
    won = out == "1"
    return {
        "worker": worker,
        "won": won,
        "rowsAffected": out,
        "elapsedMs": int((time.time() - started) * 1000),
    }


def main() -> int:
    event_id = str(uuid.uuid4())
    barrier = threading.Barrier(2)
    results = []
    with ThreadPoolExecutor(max_workers=2) as pool:
        futs = [
            pool.submit(claim, event_id, "worker-a", barrier),
            pool.submit(claim, event_id, "worker-b", barrier),
        ]
        for fut in as_completed(futs):
            results.append(fut.result())
    winners = [r for r in results if r["won"]]
    losers = [r for r in results if not r["won"]]
    count = psql(
        f"select count(*) from processed_message where consumer_name='{CONSUMER}' and event_id='{event_id}';"
    )
    passed = len(winners) == 1 and len(losers) == 1 and count.strip() == "1"
    report = {
        "eventId": event_id,
        "results": results,
        "processingRowCount": count.strip(),
        "result": "PASS" if passed else "FAIL",
        "note": "DB-level idempotency claim race (same eventId). Full Rabbit dual-delivery live path may still be exercised separately.",
    }
    REPORT.write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))
    # cleanup
    psql(
        f"delete from processed_message where consumer_name='{CONSUMER}' and event_id='{event_id}';"
    )
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
