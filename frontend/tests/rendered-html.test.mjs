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
    "UPLOADED", "VIRUS_SCANNING", "CLASSIFYING", "QUEUED", "PARSING",
    "OCR_PROCESSING", "STRUCTURE_DETECTION", "INDEXING", "READY",
    "FAILED", "MANUAL_REVIEW_REQUIRED", "CANCELLED",
  ]) {
    assert.match(documents, new RegExp(status));
  }
});

test("requirement matrix is configured by the backend and supports explanations", async () => {
  const requirements = await source("../src/modules/requirements/api.ts");
  const portal = await source("../app/page.tsx");
  assert.match(requirements, /ui-configurations\/requirement-grid/);
  assert.match(requirements, /requirement-extractions/);
  assert.match(requirements, /requirements\/\$\{requirementId\}\/explanation/);
  assert.match(portal, /grid\.columns\.filter/);
  assert.match(portal, /valueAt\(requirement, column\.key\)/);
});

test("document center uses real page clause job and cancel APIs", async () => {
  const documents = await source("../src/modules/documents/api.ts");
  assert.match(documents, /\/pages\?page=/);
  assert.match(documents, /\/clauses\?size=200/);
  assert.match(documents, /\/processing-jobs\?size=100/);
  assert.match(documents, /\/processing-jobs\/\$\{jobId\}\/cancel/);
});

test("SSE supports authorization reconnect and polling fallback", async () => {
  const api = await source("../src/modules/documents/api.ts");
  const portal = await source("../app/page.tsx");
  assert.match(api, /Authorization: `Bearer \$\{token\}`/);
  assert.match(api, /"Last-Event-ID": lastEventId/);
  assert.match(portal, /setStreamState\("polling"\)/);
  assert.match(portal, /window\.setInterval\(\(\) => load\(\)/);
});

test("clause click navigates PDF and normalized bounding boxes render", async () => {
  const portal = await source("../app/page.tsx");
  assert.match(portal, /setPage\(clause\.pageStart\)/);
  assert.match(portal, /box\.x \* 100/);
  assert.match(portal, /box\.height \* 100/);
  assert.match(portal, /source-highlights/);
});

test("parser errors manual review and tenant authorization errors are visible", async () => {
  const portal = await source("../app/page.tsx");
  const shared = await source("../src/shared/api.ts");
  assert.match(portal, /activeJob\?\.warnings/);
  assert.match(portal, /document-error/);
  assert.match(shared, /if \(!response\.ok\)/);
  assert.match(shared, /throw new ApiError/);
});

test("risk center is API-backed and grid columns are dynamic", async () => {
  const risks = await source("../src/modules/risks/api.ts");
  const portal = await source("../app/page.tsx");
  assert.match(risks, /ui-configurations\/risk-grid/);
  assert.match(risks, /tenders\/\$\{projectId\}\/risk-analyses/);
  assert.match(portal, /grid\.columns\.filter\(\(column\) => column\.visible\)/);
  assert.match(portal, /formatRiskValue\(risk, column\)/);
});

test("conflict ambiguity and change impact workspaces use real APIs", async () => {
  const risks = await source("../src/modules/risks/api.ts");
  const portal = await source("../app/page.tsx");
  assert.match(risks, /\/conflicts/);
  assert.match(risks, /\/ambiguities/);
  assert.match(risks, /\/change-sets/);
  assert.match(risks, /\/impact-analyses/);
  assert.match(portal, /function ConflictWorkspace/);
  assert.match(portal, /function AmbiguityWorkspace/);
  assert.match(portal, /function ChangeImpactWorkspace/);
});

test("grounded sources can navigate to the relevant PDF page", async () => {
  const portal = await source("../app/page.tsx");
  assert.match(portal, /downloadUrl\(token, source\.documentId\)/);
  assert.match(portal, /#page=\$\{source\.pageNumber \?\? 1\}/);
  assert.match(portal, /PDF bölgesini aç/);
});

test("dynamic knowledge center uses ontology configuration and real evidence APIs", async () => {
  const portal = await source("../app/page.tsx");
  const knowledge = await source("../src/modules/knowledge/api.ts");
  assert.match(knowledge, /ui-configurations\/entity-types/);
  assert.match(knowledge, /\/api\/v1\/knowledge\/entities/);
  assert.match(knowledge, /\/api\/v1\/evidence\/\$\{id\}/);
  assert.match(knowledge, /knowledge-extractions/);
  assert.match(portal, /configuration\.profile\.tabs\.map/);
  assert.match(portal, /dynamicValueLabel\(attribute\.value\)/);
  assert.match(portal, /EvidenceDocumentPane/);
});

test("compliance workspace is evidence-first and matrix columns come from backend", async () => {
  const portal = await source("../app/page.tsx");
  const compliance = await source("../src/modules/compliance/api.ts");
  assert.match(compliance, /tenders\/\$\{projectId\}\/compliance-analyses/);
  assert.match(compliance, /ui-configurations\/compliance-matrix/);
  assert.match(compliance, /ui-configurations\/compliance-decisions/);
  assert.match(portal, /columns\.map\(\(column\)/);
  assert.match(portal, /item\.contradiction_strength > 0/);
  assert.match(portal, /finalDecisionConceptId/);
});

test("production control center is admin-gated and API-backed", async () => {
  const portal = await source("../app/page.tsx");
  const operations = await source("../src/modules/operations/api.ts");
  assert.match(portal, /\["SYSTEM_ADMIN", "TENANT_ADMIN"\]/);
  assert.match(portal, /function OperationsCenter/);
  assert.match(portal, /Audit zinciri/);
  assert.match(portal, /Pilot readiness/);
  assert.match(operations, /\/api\/v1\/operations\/readiness/);
  assert.match(operations, /\/api\/v1\/operations\/ai-quality/);
});
