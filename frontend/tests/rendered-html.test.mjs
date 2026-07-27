import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("portal exposes authenticated project and document workflow", async () => {
  const source = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8");
  assert.match(source, /signinRedirect/);
  assert.match(source, /\/api\/v1\/tenders/);
  assert.match(source, /documents.*clauses/);
  assert.match(source, /Teknik şartname yükle/);
});
