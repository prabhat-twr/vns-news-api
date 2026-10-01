import "./style.css";

const API = (import.meta.env.VITE_API_URL || "http://localhost:8000").replace(
  /\/$/,
  "",
);
const STORE = "kashiai.chat.v1";
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
const thinking = [
  "Searching the sources…",
  "Walking the ghats…",
  "Reading the news feed…",
  "Writing your answer…",
];

function element(tag, text, cls) {
  const e = document.createElement(tag);
  if (text) e.textContent = text;
  if (cls) e.className = cls;
  return e;
}
function safeLink(url, text, cls) {
  const a = element("a", text, cls);
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
const istTime = (iso) =>
  new Date(iso).toLocaleString([], {
    timeZone: "Asia/Kolkata",
    dateStyle: "medium",
    timeStyle: "short",
  }) + " IST";

async function request(path, options = {}) {
  const response = await fetch(API + path, {
    ...options,
    signal: AbortSignal.timeout(90000),
  });
  if (!response.ok)
    throw new Error(
      response.status === 429
        ? "I'm answering someone else right now. Give me a few seconds and try again."
        : response.status === 422
          ? "I couldn't read that question. Could you rephrase it?"
          : "Something went wrong on my side. Please try again.",
    );
  return response.json();
}

/* ---------- state ---------- */
let language = "auto";
let busy = false;
let history = [];
try {
  history = JSON.parse(localStorage.getItem(STORE) || "[]");
} catch {
  history = [];
}
function save() {
  try {
    localStorage.setItem(STORE, JSON.stringify(history.slice(-40)));
  } catch {
    /* Storage can be unavailable (private mode); the chat still works. */
  }
}

/* ---------- rendering ---------- */
const chat = $("chat");
const messages = $("messages");
function scrollToEnd(smooth = true) {
  chat.scrollTo({
    top: chat.scrollHeight,
    behavior: smooth ? "smooth" : "auto",
  });
}
function syncWelcome() {
  $("welcome").hidden = messages.children.length > 0;
}

function userBubble(text) {
  const row = element("div", null, "row user");
  row.append(element("div", text, "bubble"));
  messages.append(row);
  syncWelcome();
}

function botRow() {
  const row = element("div", null, "row bot");
  row.append(element("span", "का", "avatar"));
  const body = element("div", null, "bot-body");
  row.append(body);
  messages.append(row);
  syncWelcome();
  return { row, body };
}

function renderAnswer(data, animate) {
  const { row, body } = botRow();
  const bubble = element("div", null, "bubble");
  bubble.lang = data.language;
  const sources = element("div", null, "sources");

  const paragraphs = data.answer.split(/\n{2,}/).filter((p) => p.trim());
  paragraphs.forEach((text) => {
    const p = element("p");
    // Turn [n] markers into tappable citation chips (text nodes only, no HTML).
    text.split(/(\[\d+\])/).forEach((part) => {
      const m = part.match(/^\[(\d+)\]$/);
      if (m && data.citations.some((c) => c.number === Number(m[1]))) {
        const chip = element("button", m[1], "cite");
        chip.type = "button";
        chip.setAttribute("aria-label", `Source ${m[1]}`);
        chip.addEventListener("click", () => focusSource(sources, m[1]));
        p.append(chip);
      } else if (part) p.append(document.createTextNode(part));
    });
    bubble.append(p);
  });
  body.append(bubble);

  data.warnings.forEach((w) => body.append(element("p", w, "note")));

  if (data.citations.length) {
    data.citations.forEach(({ number, record }) => {
      const card = element("article", null, `source-card ${record.kind}`);
      card.dataset.n = number;
      const top = element("div", null, "source-top");
      top.append(
        element("span", String(number), "source-n"),
        element(
          "span",
          (data.language === "hi" ? labelHi : labels)[record.kind],
          `badge ${record.kind}`,
        ),
      );
      card.append(top, safeLink(record.source_url, record.title, "source-title"));
      const meta = record.published_at
        ? `${record.publisher} · ${istTime(record.published_at)}`
        : record.reviewed_on
          ? `${record.publisher} · reviewed ${record.reviewed_on}`
          : record.publisher;
      card.append(element("small", meta));
      sources.append(card);
    });
    body.append(sources);
  }

  const meta = element("div", null, "meta");
  const mode =
    data.generation_mode === "llm"
      ? "✦ AI answer"
      : data.generation_mode === "abstained"
        ? "Need more to go on"
        : "Source extracts";
  meta.append(element("span", mode, "mode"));
  if (data.elapsed_ms)
    meta.append(element("span", `${(data.elapsed_ms / 1000).toFixed(1)}s`));
  const copy = element("button", "Copy", "ghost");
  copy.type = "button";
  copy.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(data.answer);
      copy.textContent = "Copied";
      setTimeout(() => (copy.textContent = "Copy"), 1500);
    } catch {
      copy.textContent = "Copy failed";
    }
  });
  meta.append(copy);
  body.append(meta);

  if (animate) row.classList.add("enter");
}

function focusSource(sources, n) {
  const card = sources.querySelector(`[data-n="${n}"]`);
  if (!card) return;
  card.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
  card.classList.remove("flash");
  void card.offsetWidth;
  card.classList.add("flash");
}

function renderError(text) {
  const { row, body } = botRow();
  row.classList.add("enter", "error");
  const bubble = element("div", text, "bubble");
  bubble.setAttribute("role", "alert");
  body.append(bubble);
}

function typing() {
  const { row, body } = botRow();
  row.classList.add("enter", "typing");
  const bubble = element("div", null, "bubble");
  const dots = element("span", null, "dots");
  dots.append(element("i"), element("i"), element("i"));
  const label = element("span", thinking[0], "thinking");
  bubble.append(dots, label);
  body.append(bubble);
  let i = 0;
  const started = Date.now();
  const timer = setInterval(() => {
    i += 1;
    label.textContent =
      Date.now() - started > 9000
        ? "Waking up the server — first reply can take up to a minute…"
        : thinking[i % thinking.length];
  }, 2200);
  scrollToEnd();
  return () => {
    clearInterval(timer);
    row.remove();
  };
}

/* ---------- asking ---------- */
async function ask(question, route = "auto") {
  if (busy) return;
  question = question.trim();
  if (question.length < 2) return;
  busy = true;
  setComposer("");
  $("submit").disabled = true;

  userBubble(question);
  history.push({ role: "user", text: question });
  save();
  scrollToEnd();
  const stop = typing();
  try {
    const data = await request("/api/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question, language, route }),
    });
    stop();
    renderAnswer(data, true);
    history.push({ role: "bot", data });
    save();
    setPresence(true, data.feed_status);
  } catch (error) {
    stop();
    renderError(
      error.name === "TimeoutError"
        ? "That took too long. The server may be waking up — please try again."
        : error instanceof TypeError
          ? "I can't reach the server right now. Check your connection and try again."
          : error.message,
    );
    setPresence(false);
  } finally {
    busy = false;
    syncSend();
    scrollToEnd();
  }
}

/* ---------- composer ---------- */
const input = $("question");
function setComposer(value) {
  input.value = value;
  autoGrow();
  syncSend();
}
function autoGrow() {
  input.style.height = "auto";
  input.style.height = Math.min(input.scrollHeight, 160) + "px";
}
function syncSend() {
  $("submit").disabled = busy || input.value.trim().length < 2;
}
input.addEventListener("input", () => {
  autoGrow();
  syncSend();
});
input.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
    e.preventDefault();
    $("ask-form").requestSubmit();
  }
});
$("ask-form").addEventListener("submit", (e) => {
  e.preventDefault();
  ask(input.value);
});
document.querySelectorAll("[data-question]").forEach((button) =>
  button.addEventListener("click", () =>
    ask(button.dataset.question, button.dataset.route || "auto"),
  ),
);

/* ---------- header controls ---------- */
document.querySelectorAll("[data-lang]").forEach((b) =>
  b.addEventListener("click", () => {
    language = b.dataset.lang;
    document
      .querySelectorAll("[data-lang]")
      .forEach((x) => x.setAttribute("aria-checked", String(x === b)));
  }),
);
$("new-chat").addEventListener("click", () => {
  if (busy) return;
  history = [];
  save();
  messages.replaceChildren();
  syncWelcome();
  input.focus();
});

function setPresence(online, feed) {
  const el = $("connection");
  el.classList.toggle("offline", !online);
  el.textContent = online ? "Online" : "Offline · try again";
  if (feed) {
    const date = feed.latest_publication ? istTime(feed.latest_publication) : "unknown";
    $("freshness").textContent =
      `${feed.origin === "live_feed" ? "Live news feed" : "Repository snapshot"} · latest report ${date}${feed.stale ? " · may be out of date" : ""}`;
  }
}
async function health() {
  try {
    const data = await request("/health");
    setPresence(true, data.news);
  } catch {
    setPresence(false);
  }
}

/* ---------- source library ---------- */
function closeLibrary() {
  $("library").hidden = true;
  $("source-toggle").focus();
}
$("source-toggle").addEventListener("click", async () => {
  $("library").hidden = false;
  $("source-close").focus();
  $("library-items").textContent = "Loading sources…";
  try {
    const { knowledge } = await request("/api/sources");
    $("library-items").replaceChildren(
      ...knowledge.map((r) => {
        const item = element("article", null, `library-item ${r.kind}`);
        item.append(
          safeLink(r.source_url, r.title, "source-title"),
          element(
            "small",
            `${labels[r.kind]} · ${r.publisher}${r.reviewed_on ? " · reviewed " + r.reviewed_on : ""}`,
          ),
        );
        return item;
      }),
    );
  } catch {
    $("library-items").textContent =
      "The source library isn't available right now. Please try again.";
  }
});
document
  .querySelectorAll("[data-close]")
  .forEach((el) => el.addEventListener("click", closeLibrary));
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape" && !$("library").hidden) closeLibrary();
});

/* ---------- boot ---------- */
history.forEach((m) =>
  m.role === "user" ? userBubble(m.text) : renderAnswer(m.data, false),
);
syncWelcome();
scrollToEnd(false);
health();
