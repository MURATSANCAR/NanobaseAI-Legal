import importlib
import os
import uuid

import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def service(tmp_path, monkeypatch):
    monkeypatch.setenv("JOB_DATABASE_PATH", str(tmp_path / "jobs.sqlite3"))
    monkeypatch.setenv("TEMPORARY_DIRECTORY", str(tmp_path / "temp"))
    monkeypatch.setenv("S3_SOURCE_BUCKET", "specai-original")
    import app

    importlib.reload(app)
    app.initialize_database()
    monkeypatch.setattr(app, "process_document", lambda *_: None)
    return app


def request_body(organization_id, correlation_id=None):
    return {
        "documentVersionId": str(uuid.uuid4()),
        "source": {
            "type": "S3_COMPATIBLE",
            "bucket": "specai-original",
            "objectKey": (
                f"specai-original/{organization_id}/{uuid.uuid4()}/"
                f"{uuid.uuid4()}/{uuid.uuid4()}/spec.pdf"
            ),
        },
        "mimeType": "application/pdf",
        "languageHint": "tr",
        "ocrMode": "AUTO",
        "extractTables": True,
        "extractImages": False,
        "correlationId": str(correlation_id or uuid.uuid4()),
    }


def test_health_and_idempotent_submission(service):
    client = TestClient(service.app)
    assert client.get("/health/live").json() == {"status": "UP"}
    correlation_id = uuid.uuid4()
    body = request_body(uuid.uuid4(), correlation_id)
    first = client.post("/v1/documents/parse", json=body)
    second = client.post("/v1/documents/parse", json=body)
    assert first.status_code == 202
    assert second.status_code == 202
    assert first.json()["jobId"] == second.json()["jobId"]


def test_rejects_cross_tenant_or_unsafe_object_key(service):
    client = TestClient(service.app)
    body = request_body(uuid.uuid4())
    body["source"]["objectKey"] = "../other-tenant/spec.pdf"
    response = client.post("/v1/documents/parse", json=body)
    assert response.status_code == 400


def test_normalized_box_uses_top_left_coordinates(service):
    box = service.normalized_box(
        {"page_no": 1, "bbox": {"l": 20, "t": 10, "r": 120, "b": 60}},
        {1: (200, 100)},
    )
    assert box == {
        "page": 1,
        "x": 0.1,
        "y": 0.1,
        "width": 0.5,
        "height": 0.5,
    }


def test_clause_number_is_derived_only_from_real_heading_text(service):
    assert service.extract_clause_number("12.4 Teknik gereklilikler") == "12.4"
    assert service.extract_clause_number("Teknik gereklilikler") is None


def test_reflow_attaches_body_and_skips_running_headers(service):
    items = [
        {
            "index": 0,
            "text": "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi",
            "normalized": "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi",
            "label": "section_header",
            "boxes": [{"page": 3, "x": 0.1, "y": 0.05, "width": 0.8, "height": 0.03}],
            "page": 3,
        },
        {
            "index": 1,
            "text": "1.7.4 Yüklenici, Lokasyon ve Bina Detaylar",
            "normalized": "1.7.4 Yüklenici, Lokasyon ve Bina Detaylar",
            "label": "section_header",
            "boxes": [{"page": 4, "x": 0.1, "y": 0.2, "width": 0.7, "height": 0.03}],
            "page": 4,
        },
        {
            "index": 2,
            "text": "1.7.4.2 Yüklenicinin Ana Veri Merkezi en az Tier III sertifikasına sahip olmalıdır.",
            "normalized": (
                "1.7.4.2 Yüklenicinin Ana Veri Merkezi en az Tier III "
                "sertifikasına sahip olmalıdır."
            ),
            "label": "text",
            "boxes": [{"page": 5, "x": 0.1, "y": 0.3, "width": 0.8, "height": 0.04}],
            "page": 5,
        },
        {
            "index": 3,
            "text": "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi",
            "normalized": "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi",
            "label": "section_header",
            "boxes": [{"page": 5, "x": 0.1, "y": 0.05, "width": 0.8, "height": 0.03}],
            "page": 5,
        },
        {
            "index": 4,
            "text": "TİM © Ocak 2026",
            "normalized": "TİM © Ocak 2026",
            "label": "text",
            "boxes": [{"page": 5, "x": 0.1, "y": 0.95, "width": 0.3, "height": 0.02}],
            "page": 5,
        },
        {
            "index": 5,
            "text": "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi",
            "normalized": "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi",
            "label": "section_header",
            "boxes": [{"page": 6, "x": 0.1, "y": 0.05, "width": 0.8, "height": 0.03}],
            "page": 6,
        },
        {
            "index": 6,
            "text": "1.7.5 Genel Hizmetler ve Detaylar",
            "normalized": "1.7.5 Genel Hizmetler ve Detaylar",
            "label": "section_header",
            "boxes": [{"page": 5, "x": 0.1, "y": 0.5, "width": 0.6, "height": 0.03}],
            "page": 5,
        },
        {
            "index": 7,
            "text": "YÜKLENİCİ destek hizmetlerini 7x24 sunacaktır.",
            "normalized": "YÜKLENİCİ destek hizmetlerini 7x24 sunacaktır.",
            "label": "text",
            "boxes": [{"page": 5, "x": 0.1, "y": 0.55, "width": 0.7, "height": 0.03}],
            "page": 5,
        },
    ]
    noise = service.detect_running_noise(items)
    assert "Altyapı & Yönetilen Hizmetler Teknik Şartnamesi" in noise
    clauses = service.build_reflowed_clauses(items, noise)
    assert len(clauses) == 2
    first = clauses[0]
    assert first["title"].startswith("1.7.4")
    assert "Tier III" in first["rawText"]
    assert first["pageStart"] == 4
    assert first["pageEnd"] == 5
    assert first["metadata"]["reflowed"] is True
    second = clauses[1]
    assert second["title"].startswith("1.7.5")
    assert "7x24" in second["rawText"]

