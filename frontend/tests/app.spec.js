import { test, expect } from "@playwright/test";
const status = {
  origin: "repository_snapshot",
  latest_publication: null,
  stale: true,
};
test.beforeEach(async ({ page }) => {
  await page.route("**/health", (route) =>
    route.fulfill({ json: { retrieval_mode: "bm25", news: status } }),
  );
});
test("question, safe citations and source labels", async ({ page }) => {
  await page.route("**/api/ask", (route) =>
    route.fulfill({
      json: {
        answer: "Sarnath is linked to the first teaching. [1]",
        language: "en",
        generation_mode: "extractive",
        route: "knowledge",
        warnings: [],
        citations: [
          {
            number: 1,
            record: {
              title: "<script>alert(1)</script> Sarnath",
              source_url: "https://varanasi.nic.in/",
              kind: "historical_fact",
              publisher: "District Varanasi",
              reviewed_on: "2026-09-30",
            },
          },
        ],
        trace: ["route:knowledge", "tool:search_knowledge"],
        retrieval_mode: "bm25",
        elapsed_ms: 20,
        feed_status: status,
      },
    }),
  );
  await page.goto("/");
  await page
    .getByLabel("What would you like to discover?")
    .fill("Tell me about Sarnath");
  await page.getByRole("button", { name: "Ask KashiAI" }).click();
  await expect(page.locator("#answer")).toContainText("first teaching");
  await expect(page.locator(".citation")).toContainText("Historical fact");
  await expect(page.locator(".citation script")).toHaveCount(0);
  await expect(page.locator(".citation a")).toHaveAttribute(
    "rel",
    "noopener noreferrer",
  );
  await expect(page.locator("#answer-mode")).toHaveText("Source extracts");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});
test("offline errors recover and inputs remain usable", async ({ page }) => {
  await page.route("**/api/ask", (route) => route.abort());
  await page.goto("/");
  await page.getByRole("button", { name: "History of the ghats" }).click();
  await expect(page.getByRole("alert")).toContainText("Cannot reach");
  await expect(page.getByRole("button", { name: "Ask KashiAI" })).toBeEnabled();
});
test("date validation and source library", async ({ page }) => {
  await page.route("**/api/sources", (route) =>
    route.fulfill({
      json: {
        knowledge: [
          {
            title: "Sarnath",
            source_url: "https://varanasi.nic.in/",
            kind: "official_information",
            publisher: "District",
            reviewed_on: "2026-09-30",
          },
        ],
      },
    }),
  );
  await page.goto("/");
  await page.getByText("Focus your search").click();
  await page.locator("#question").fill("flood news");
  await page.locator("#since").fill("2026-02-01");
  await page.locator("#until").fill("2026-01-01");
  await page.getByRole("button", { name: "Ask KashiAI" }).click();
  await expect(page.getByRole("alert")).toContainText("start date");
  await page.getByRole("button", { name: "Source library" }).click();
  await expect(page.locator("#library-items")).toContainText("Sarnath");
});
