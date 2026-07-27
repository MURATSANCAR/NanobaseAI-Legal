import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function source(path) {
  return readFile(new URL(path, import.meta.url), "utf8");
}

test("portal uses protected OIDC PKCE session flow", async () => {
  const auth = await source("../src/modules/auth/auth.ts");
  assert.match(auth, /response_type: "code"/);
  assert.match(auth, /signinRedirectCallback/);
  assert.match(auth, /automaticSilentRenew: true/);
});

test("project creation and document upload use the real API", async () => {
  const tenders = await source("../src/modules/tenders/api.ts");
  const documents = await source("../src/modules/documents/api.ts");
  assert.match(tenders, /\/api\/v1\/tenders/);
  assert.match(documents, /documentType/);
  assert.match(documents, /includedInAnalysis/);
  assert.match(documents, /\/versions/);
  assert.match(documents, /\/reprocess/);
  assert.match(documents, /\/download-url/);
});

test("processing badges cover every backend status", async () => {
  const documents = await source("../src/modules/documents/api.ts");
  for (const status of [
    "UPLOADED", "VIRUS_SCANNING", "CLASSIFYING", "PARSING",
    "OCR_PROCESSING", "STRUCTURE_DETECTION", "INDEXING", "READY",
    "FAILED", "MANUAL_REVIEW_REQUIRED",
  ]) {
    assert.match(documents, new RegExp(status));
  }
});
