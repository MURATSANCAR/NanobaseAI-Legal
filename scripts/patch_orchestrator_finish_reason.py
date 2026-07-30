#!/usr/bin/env python3
"""Patch ai-orchestrator app.py to log finish_reason / truncation metadata."""
from __future__ import annotations

from pathlib import Path

path = Path("/tmp/app.py")
text = path.read_text()
old = '''    runtime_response = response.json()
    try:
        content = runtime_response["choices"][0]["message"]["content"]
        output = content if isinstance(content, dict) else json.loads(content)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        raise HTTPException(
            status_code=502,
            detail={
                "code": "LLM_INVALID_RESPONSE",
                "deploymentAlias": deployment.deployment_alias,
            },
        ) from exc'''
new = '''    runtime_response = response.json()
    choice0 = (runtime_response.get("choices") or [{}])[0] or {}
    finish_reason = choice0.get("finish_reason")
    message = choice0.get("message") or {}
    content = message.get("content")
    content_len = len(content) if isinstance(content, str) else (
        len(json.dumps(content, ensure_ascii=False)) if content is not None else 0
    )
    usage_meta = runtime_response.get("usage") or {}
    LOG.info(
        "model_response_meta correlation_id=%s finishReason=%s contentChars=%s "
        "promptTokens=%s completionTokens=%s truncated=%s",
        correlation_id,
        finish_reason,
        content_len,
        usage_meta.get("prompt_tokens"),
        usage_meta.get("completion_tokens"),
        finish_reason in {"length", "max_tokens"},
    )
    try:
        output = content if isinstance(content, dict) else json.loads(content)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        LOG.warning(
            "model_parse_error correlation_id=%s finishReason=%s contentChars=%s "
            "contentPrefix=%r errorType=%s",
            correlation_id,
            finish_reason,
            content_len,
            (content[:240] if isinstance(content, str) else content),
            type(exc).__name__,
        )
        raise HTTPException(
            status_code=502,
            detail={
                "code": "LLM_INVALID_RESPONSE",
                "deploymentAlias": deployment.deployment_alias,
                "finishReason": finish_reason,
                "contentChars": content_len,
                "truncated": finish_reason in {"length", "max_tokens"},
            },
        ) from exc'''
if old not in text:
    raise SystemExit("patch target not found")
path.write_text(text.replace(old, new, 1))
print("PATCH_OK")
