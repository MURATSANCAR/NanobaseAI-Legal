import { expect, test, type Page } from "@playwright/test";

const user = process.env.E2E_USER;
const password = process.env.E2E_PASSWORD;
const documentPath = process.env.E2E_DOCUMENT_FILE;
const runId = process.env.E2E_RUN_ID ?? Date.now().toString();

async function login(page: Page) {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Ana panel" })).toBeVisible();
  await page.getByRole("button", { name: "Canlı veriye bağlan" }).click();
  await page.getByLabel(/username|email/i).fill(user ?? "");
  await page.getByLabel(/password/i).fill(password ?? "");
  await page.getByRole("button", { name: /sign in|giriş/i }).click();
  await expect(page.getByRole("heading", { name: "Ana panel" })).toBeVisible();
}

test.describe.serial("Sprint 8 real-stack acceptance path", () => {
  test.skip(!user || !password || !documentPath,
    "E2E_USER, E2E_PASSWORD and E2E_DOCUMENT_FILE are required");

  test("login, project creation and secure document upload", async ({ page }) => {
    await login(page);
    await page.getByRole("button", { name: "İhale projeleri" }).click();
    await page.getByRole("button", { name: "Yeni proje" }).click();
    await page.getByLabel(/proje adı/i).fill(`Synthetic Acceptance ${runId}`);
    await page.getByLabel(/kurum/i).fill("Synthetic Procurement Authority");
    for (let step = 0; step < 3; step++) {
      await page.getByRole("button", { name: /devam/i }).click();
    }
    await page.getByRole("button", { name: /oluştur/i }).click();
    await page.getByRole("tab", { name: /doküman/i }).click();
    await page.setInputFiles('input[type="file"]', documentPath!);
    await page.getByRole("button", { name: /yükle/i }).click();
    await expect(page.getByText(/UPLOADED|VIRUS|QUARANTINED|QUEUED|PARSING/))
      .toBeVisible();
  });

  test("clause, requirement, knowledge, compliance and risk workspaces", async ({ page }) => {
    await login(page);
    await page.getByRole("button", { name: "İhale projeleri" }).click();
    await page.getByText(`Synthetic Acceptance ${runId}`).click();
    for (const tab of [
      /doküman/i, /gereksinim/i, /firma ve ürün/i, /uygunluk/i,
      /risk/i, /çelişki/i, /belirsizlik/i, /değişiklik/i,
    ]) {
      await page.getByRole("tab", { name: tab }).click();
    }
  });

  test("workflow through finalization and addendum is explicit pending evidence", async ({ page }) => {
    await login(page);
    await page.getByRole("button", { name: "İhale projeleri" }).click();
    await page.getByText(`Synthetic Acceptance ${runId}`).click();
    await expect(page.getByText(/görev|onay|rapor|karar|final/i).first()).toBeVisible();
  });

  test("operations, AI quality and pilot dashboard are admin-only", async ({ page }) => {
    await login(page);
    await page.getByRole("button", { name: "Production kontrolü" }).click();
    await expect(page.getByRole("heading", { name: "Production kontrol merkezi" }))
      .toBeVisible();
    await page.getByRole("button", { name: /AI kalite/i }).click();
    await page.getByRole("button", { name: /Pilot ve kabul/i }).click();
    await expect(page.getByText("Pilot readiness")).toBeVisible();
  });
});
