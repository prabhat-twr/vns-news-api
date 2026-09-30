import "./style.css";

const API = (import.meta.env.VITE_API_URL || "http://localhost:8000").replace(
  /\/$/,
  "",
);
const $ = (id) => document.getElementById(id);
const labels = {
  historical_fact: "Historical fact",
  official_information: "Official information",
  religious_tradition: "Religious tradition",
  current_news: "Current news",
};
const labelHi = {
  historical_fact: "ऐतिहासिक तथ्य",
  official_information: "आधिकारिक जानकारी",
  religious_tradition: "धार्मिक परंपरा",
  current_news: "समाचार रिपोर्ट",
};
function element(tag, text, cls) {
  const e = document.createElement(tag);
  if (text) e.textContent = text;
  if (cls) e.className = cls;
  return e;
}
function safeLink(url, text) {
  const a = element("a", text);
  try {
    const u = new URL(url);
    if (["http:", "https:"].includes(u.protocol)) a.href = u.href;
  } catch {
    /* Invalid source URLs are not made clickable. */
  }
  a.target = "_blank";
  a.rel = "noopener noreferrer";
  return a;
}
async function request(path, options = {}) {
  const response = await fetch(API + path, {
    ...options,
    signal: AbortSignal.timeout(65000),
  });
  if (!response.ok)
    throw new Error(
      response.status === 429
        ? "KashiAI is busy. Please try again shortly."
        : response.status === 422
          ? "Check your question and date range."
          : "The assistant is temporarily unavailable. Please try again.",
    );
  return response.json();
}
function feedStatus(status) {
  const date = status.latest_publication
    ? new Date(status.latest_publication).toLocaleString([], {
        timeZone: "Asia/Kolkata",
      }) + " IST"
    : "unknown";
  $("freshness").textContent =
    `${status.origin === "live_feed" ? "Feed connected" : "Repository snapshot"} · Latest dated report: ${date}${status.stale ? " · May be out of date" : ""}`;
}
async function health() {
  try {
    const data = await request("/health");
    $("connection").textContent =
      data.retrieval_mode === "bm25"
        ? "● Cited search ready"
        : "● Hybrid search ready";
    feedStatus(data.news);
  } catch {
    $("connection").textContent = "○ Backend offline";
    $("freshness").textContent =
      "News freshness unavailable. Start or connect the backend to ask questions.";
  }
}
let busy = false;
$("ask-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy) return;
  const question = $("question").value.trim();
  if (question.length < 2) return;
  if (
    $("since").value &&
    $("until").value &&
    $("since").value > $("until").value
  ) {
    $("error").hidden = false;
    $("error").textContent =
      "The start date must be on or before the end date.";
    return;
  }
  busy = true;
  $("submit").disabled = true;
  $("submit").textContent = "Finding sources…";
  $("error").hidden = true;
  $("result").hidden = true;
  $("empty").hidden = true;
  try {
    const data = await request("/api/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        question,
        language: $("language").value,
        route: $("route").value,
        since: $("since").value || null,
        until: $("until").value || null,
      }),
    });
    $("asked").textContent = question;
    $("answer").replaceChildren();
    $("answer").lang = data.language;
    data.answer
      .split("\n\n")
      .forEach((text) => $("answer").append(element("p", text)));
    $("answer-mode").textContent =
      data.generation_mode === "llm"
        ? "AI synthesis"
        : data.generation_mode === "abstained"
          ? "More evidence needed"
          : "Source extracts";
    $("warnings").replaceChildren(
      ...data.warnings.map((w) => element("p", w, "warning")),
    );
    $("citations").replaceChildren();
    data.citations.forEach(({ number, record }) => {
      const card = element("article", null, "citation");
      const title = safeLink(
        record.source_url,
        `[${number}] ${record.title} ↗`,
      );
      const meta = element("div", null, "citation-meta");
      meta.append(
        element(
          "span",
          (data.language === "hi" ? labelHi : labels)[record.kind],
          `badge ${record.kind}`,
        ),
      );
      meta.append(element("span", record.publisher));
      if (record.published_at)
        meta.append(
          element(
            "time",
            new Date(record.published_at).toLocaleString([], {
              timeZone: "Asia/Kolkata",
            }) + " IST",
          ),
        );
      else if (record.kind === "current_news")
        meta.append(element("span", "Publication date unknown"));
      else meta.append(element("span", `Reviewed ${record.reviewed_on}`));
      card.append(title, meta);
      $("citations").append(card);
    });
    $("trace").textContent =
      `${data.trace.join(" → ")} · ${data.retrieval_mode} · ${Math.round(data.elapsed_ms)} ms. Citations identify evidence; they do not independently verify a claim.`;
    feedStatus(data.feed_status);
    $("result").hidden = false;
  } catch (error) {
    $("error").textContent =
      error.name === "TimeoutError"
        ? "The request took too long. Try again in a moment."
        : error instanceof TypeError
          ? "Cannot reach the backend. Check that it is running and the website is allowed to connect."
          : error.message;
    $("error").hidden = false;
    $("empty").hidden = false;
  } finally {
    busy = false;
    $("submit").disabled = false;
    $("submit").textContent = "Ask KashiAI ↗";
  }
});
document.querySelectorAll("[data-question]").forEach((button) =>
  button.addEventListener("click", () => {
    if (busy) return;
    $("question").value = button.dataset.question;
    $("route").value = button.dataset.route || "auto";
    $("since").value = "";
    $("until").value = "";
    $("ask-form").requestSubmit();
  }),
);
$("source-toggle").addEventListener("click", async () => {
  $("library").hidden = false;
  $("library").scrollIntoView({ behavior: "smooth" });
  $("library-items").textContent = "Loading sources…";
  try {
    const { knowledge } = await request("/api/sources");
    $("library-items").replaceChildren(
      ...knowledge.map((r) => {
        const item = element("article", null, "library-item");
        item.append(
          safeLink(r.source_url, r.title + " ↗"),
          element(
            "small",
            `${labels[r.kind]} · ${r.publisher} · Reviewed ${r.reviewed_on}`,
          ),
        );
        return item;
      }),
    );
  } catch {
    $("library-items").textContent =
      "Source library unavailable. Please connect the backend and try again.";
  }
});
$("source-close").addEventListener("click", () => {
  $("library").hidden = true;
  $("source-toggle").focus();
});
health();
