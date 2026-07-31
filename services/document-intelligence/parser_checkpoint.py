"""Idempotent page/batch checkpoints for bounded document parsing."""

from __future__ import annotations

import hashlib
import json
import sqlite3
import threading
from pathlib import Path
from typing import Any


class ParserCheckpointStore:
    def __init__(self, database_path: Path, lock: threading.Lock):
        self.database_path = database_path
        self.lock = lock

    def _connect(self) -> sqlite3.Connection:
        database = sqlite3.connect(self.database_path, timeout=30)
        database.row_factory = sqlite3.Row
        return database

    def initialize(self) -> None:
        with self.lock, self._connect() as database:
            database.execute(
                """
                CREATE TABLE IF NOT EXISTS parser_page_checkpoint (
                    id TEXT PRIMARY KEY,
                    organization_id TEXT,
                    document_version_id TEXT NOT NULL,
                    parser_job_id TEXT NOT NULL,
                    page_number INTEGER,
                    batch_number INTEGER NOT NULL,
                    provider TEXT NOT NULL,
                    provider_version TEXT,
                    status TEXT NOT NULL,
                    attempt INTEGER NOT NULL,
                    started_at TEXT,
                    completed_at TEXT,
                    duration_ms INTEGER,
                    text_block_count INTEGER,
                    layout_block_count INTEGER,
                    table_count INTEGER,
                    ocr_used INTEGER NOT NULL DEFAULT 0,
                    quality_status TEXT,
                    error_code TEXT,
                    artifact_reference TEXT,
                    content_hash TEXT,
                    lease_generation INTEGER NOT NULL DEFAULT 0,
                    result_json TEXT,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(parser_job_id, batch_number, attempt)
                )
                """
            )
            database.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_parser_checkpoint_job
                ON parser_page_checkpoint(parser_job_id, status)
                """
            )

    def latest_success_by_batch(self, job_id: str) -> dict[int, dict[str, Any]]:
        with self._connect() as database:
            rows = database.execute(
                """
                SELECT *
                FROM parser_page_checkpoint
                WHERE parser_job_id = ? AND status = 'SUCCEEDED'
                ORDER BY batch_number ASC, attempt DESC
                """,
                (job_id,),
            ).fetchall()
        latest: dict[int, dict[str, Any]] = {}
        for row in rows:
            batch_number = int(row["batch_number"])
            if batch_number in latest:
                continue
            payload = dict(row)
            if payload.get("result_json"):
                payload["result"] = json.loads(payload["result_json"])
            latest[batch_number] = payload
        return latest

    def list_for_job(self, job_id: str) -> list[dict[str, Any]]:
        with self._connect() as database:
            rows = database.execute(
                """
                SELECT *
                FROM parser_page_checkpoint
                WHERE parser_job_id = ?
                ORDER BY batch_number ASC, attempt ASC
                """,
                (job_id,),
            ).fetchall()
        return [dict(row) for row in rows]

    def save(self, record: dict[str, Any]) -> None:
        content = record.get("result")
        result_json = json.dumps(content, ensure_ascii=False) if content is not None else None
        content_hash = record.get("content_hash")
        if content_hash is None and result_json is not None:
            content_hash = hashlib.sha256(result_json.encode("utf-8")).hexdigest()
        with self.lock, self._connect() as database:
            existing = database.execute(
                """
                SELECT id FROM parser_page_checkpoint
                WHERE parser_job_id = ? AND batch_number = ? AND attempt = ?
                """,
                (record["parser_job_id"], record["batch_number"], record["attempt"]),
            ).fetchone()
            if existing:
                database.execute(
                    """
                    UPDATE parser_page_checkpoint
                    SET status = ?, completed_at = ?, duration_ms = ?,
                        text_block_count = ?, layout_block_count = ?, table_count = ?,
                        ocr_used = ?, quality_status = ?, error_code = ?,
                        artifact_reference = ?, content_hash = ?, result_json = ?,
                        provider = ?, provider_version = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (
                        record["status"],
                        record.get("completed_at"),
                        record.get("duration_ms"),
                        record.get("text_block_count"),
                        record.get("layout_block_count"),
                        record.get("table_count"),
                        1 if record.get("ocr_used") else 0,
                        record.get("quality_status"),
                        record.get("error_code"),
                        record.get("artifact_reference"),
                        content_hash,
                        result_json,
                        record.get("provider"),
                        record.get("provider_version"),
                        existing["id"],
                    ),
                )
                return
            database.execute(
                """
                INSERT INTO parser_page_checkpoint (
                    id, organization_id, document_version_id, parser_job_id,
                    page_number, batch_number, provider, provider_version, status,
                    attempt, started_at, completed_at, duration_ms, text_block_count,
                    layout_block_count, table_count, ocr_used, quality_status,
                    error_code, artifact_reference, content_hash, lease_generation,
                    result_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    record["id"],
                    record.get("organization_id"),
                    record["document_version_id"],
                    record["parser_job_id"],
                    record.get("page_number"),
                    record["batch_number"],
                    record["provider"],
                    record.get("provider_version"),
                    record["status"],
                    record["attempt"],
                    record.get("started_at"),
                    record.get("completed_at"),
                    record.get("duration_ms"),
                    record.get("text_block_count"),
                    record.get("layout_block_count"),
                    record.get("table_count"),
                    1 if record.get("ocr_used") else 0,
                    record.get("quality_status"),
                    record.get("error_code"),
                    record.get("artifact_reference"),
                    content_hash,
                    record.get("lease_generation", 0),
                    result_json,
                ),
            )
