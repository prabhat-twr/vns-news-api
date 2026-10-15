// Run against a locally running backend/frontend. Captures real application states.
import { chromium } from "@playwright/test";
import { mkdir } from "node:fs/promises";
await mkdir("../docs/screenshots", { recursive: true });
const browser = await chromium.launch();
try {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1050 },
  });
  await page.goto("http://127.0.0.1:5173");
  await page
    .locator("#connection")
    .filter({ hasText: /ready|offline/ })
    .waitFor();
  await page.screenshot({
    path: "../docs/screenshots/desktop.png",
    fullPage: true,
  });
  await page
    .getByLabel("What would you like to discover?")
    .fill("सारनाथ का क्या महत्व है?");
  await page.getByRole("button", { name: "Ask KashiAI" }).click();
  await page.locator("#result").waitFor({ state: "visible", timeout: 65000 });
  await page.screenshot({
    path: "../docs/screenshots/cited-answer.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: "../docs/screenshots/mobile.png",
    fullPage: true,
  });
} finally {
  await browser.close();
}
