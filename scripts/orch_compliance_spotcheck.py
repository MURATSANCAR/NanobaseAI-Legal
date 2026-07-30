#!/usr/bin/env python3
"""Single orchestrator HTTP spot-check for the same 1-req/1-evidence payload."""
from __future__ import annotations

import json
import subprocess
import uuid
from pathlib import Path

ORG = "11111111-1111-1111-1111-111111111111"
REQ = "184e7eac-7808-4b79-86df-a70bf619bc33"
EV = "4cd5fd0c-51cf-4d6f-a63c-126fad74b960"


def env(key: str) -> str:
    out = subprocess.check_output(
        ["sudo", "grep", f"^{key}=", "/etc/nanobaseai/legal.env"], text=True
    )
    return out.strip().split("=", 1)[1]


def psql(sql: str) -> str:
    Path("/tmp/o.sql").write_text(sql)
    subprocess.check_call(
        ["sudo", "docker", "cp", "/tmp/o.sql", "actenora-prodlike-postgres:/tmp/o.sql"]
    )
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
            "-At",
            "-f",
            "/tmp/o.sql",
        ],
        text=True,
    ).strip()


def main() -> None:
    schema = json.loads(
        psql(
            f"select set_config('app.current_organization_id','{ORG}',true);\n"
            "select v.json_schema::text from output_schema_definition d "
            "join output_schema_version v on v.id=d.active_version_id "
            "where d.schema_code='BASE_COMPLIANCE_V1';"
        ).splitlines()[-1]
    )
    safety = psql(
        f"select set_config('app.current_organization_id','{ORG}',true);\n"
        "select content_template from prompt_component where component_code='BASE_SAFETY';"
    ).splitlines()[-1]
    task = psql(
        f"select set_config('app.current_organization_id','{ORG}',true);\n"
        "select content_template from prompt_component "
        "where component_code='COMPLIANCE_EVALUATION_TASK';"
    ).splitlines()[-1]
    req = psql(
        f"select set_config('app.current_organization_id','{ORG}',true);\n"
        f"select requirement_text from requirement where id='{REQ}';"
    ).splitlines()[-1]
    evid = psql(
        f"select set_config('app.current_organization_id','{ORG}',true);\n"
        f"select fragment_text from evidence_fragment where id='{EV}';"
    ).splitlines()[-1]
    payload = {
        "model": "nanobase-spec-ai",
        "profile": "BALANCED",
        "promptComponents": [safety, task],
        "outputSchema": schema,
        "requirement": {"id": REQ, "text": req},
        "ontologyConcepts": [
            {"code": "COMPLIANT", "metadata": {"positive": True}},
            {"code": "PARTIALLY_COMPLIANT", "metadata": {"positive": True}},
            {"code": "NON_COMPLIANT", "metadata": {"positive": False}},
            {"code": "INSUFFICIENT_INFORMATION", "metadata": {"positive": False}},
        ],
        "evidence": [{"id": EV, "text": evid}],
        "allowedDecisionConcepts": [
            "COMPLIANT",
            "PARTIALLY_COMPLIANT",
            "NON_COMPLIANT",
            "INSUFFICIENT_INFORMATION",
        ],
        "maximumOutputTokens": 1024,
    }
    Path("/tmp/orch_payload.json").write_text(json.dumps(payload))
    net = subprocess.check_output(
        [
            "sudo",
            "docker",
            "inspect",
            "specai-legal-ai-orchestrator-1",
            "-f",
            "{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}",
        ],
        text=True,
    ).strip().split()[0]
    cid = str(uuid.uuid4())
    out = subprocess.check_output(
        [
            "sudo",
            "docker",
            "run",
            "--rm",
            "--network",
            net,
            "-v",
            "/tmp/orch_payload.json:/payload.json:ro",
            "curlimages/curl:8.5.0",
            "-sS",
            "-m",
            "820",
            "-w",
            "\nHTTP=%{http_code}\n",
            "-H",
            "Content-Type: application/json",
            "-H",
            f"X-Correlation-ID: {cid}",
            "-d",
            "@/payload.json",
            "http://ai-orchestrator:8090/v1/compliance-evaluations",
        ],
        text=True,
        errors="replace",
    )
    print(out[-1000:])
    print("correlationId", cid)


if __name__ == "__main__":
    main()
