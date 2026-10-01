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
  await page.getByLabel("Message KashiAI").fill("Tell me about Sarnath");
  await page.getByLabel("Message KashiAI").press("Enter");
  await expect(page.locator(".row.user .bubble")).toHaveText(
    "Tell me about Sarnath",
  );
  await expect(page.locator(".row.bot .bubble")).toContainText(
    "first teaching",
  );
  await expect(page.locator(".cite")).toHaveText("1");
  await expect(page.locator(".source-card")).toContainText("Historical fact");
  await expect(page.locator(".source-card script")).toHaveCount(0);
  await expect(page.locator(".source-card a")).toHaveAttribute(
    "rel",
    "noopener noreferrer",
  );
  await expect(page.locator(".mode")).toHaveText("Source extracts");
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});
test("offline errors recover and inputs remain usable", async ({ page }) => {
  await page.route("**/api/ask", (route) => route.abort());
  await page.goto("/");
  await page.getByRole("button", { name: /History of the ghats/ }).click();
  await expect(page.getByRole("alert")).toContainText("can't reach");
  await page.getByLabel("Message KashiAI").fill("Try again");
  await expect(page.getByRole("button", { name: "Send" })).toBeEnabled();
});
test("source library and new chat", async ({ page }) => {
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
  await page.getByRole("button", { name: "Source library" }).click();
  await expect(page.locator("#library-items")).toContainText("Sarnath");
  await page.keyboard.press("Escape");
  await expect(page.locator("#library")).toBeHidden();
  await page.getByRole("button", { name: "New chat" }).click();
  await expect(page.getByText("Namaste! I'm KashiAI.")).toBeVisible();
});
test("follow-up questions carry the conversation", async ({ page }) => {
  const bodies = [];
  await page.route("**/api/ask", async (route) => {
    const body = route.request().postDataJSON();
    bodies.push(body);
    await route.fulfill({
      json: {
        answer: `Answer ${bodies.length}`,
        language: "en",
        generation_mode: "llm",
        route: "knowledge",
        warnings: [],
        citations: [],
        trace: [],
        retrieval_mode: "bm25",
        elapsed_ms: 10,
        feed_status: status,
      },
    });
  });
  await page.goto("/");
  const box = page.getByLabel("Message KashiAI");
  await box.fill("Tell me about Sarnath");
  await box.press("Enter");
  await expect(page.locator(".row.bot .bubble")).toHaveText("Answer 1");
  await box.fill("tell me more");
  await box.press("Enter");
  await expect(page.locator(".row.bot .bubble").last()).toHaveText("Answer 2");
  expect(bodies[0].history).toEqual([]);
  expect(bodies[1].history).toEqual([
    { role: "user", content: "Tell me about Sarnath" },
    { role: "assistant", content: "Answer 1" },
  ]);
});
