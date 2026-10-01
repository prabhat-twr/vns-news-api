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
  event: "Event",
};
const labelHi = {
  historical_fact: "ऐतिहासिक तथ्य",
  official_information: "आधिकारिक जानकारी",
  religious_tradition: "धार्मिक परंपरा",
  current_news: "समाचार रिपोर्ट",
  event: "कार्यक्रम",
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

  // The last few turns let the server understand follow-ups like "tell me more".
  const turns = history.slice(-6).map((m) =>
    m.role === "user"
      ? { role: "user", content: m.text.slice(0, 4000) }
      : { role: "assistant", content: m.data.answer.slice(0, 4000) },
  );
  userBubble(question);
  history.push({ role: "user", text: question });
  save();
  scrollToEnd();
  const stop = typing();
  try {
    const data = await request("/api/ask", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question, language, route, history: turns }),
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

/* ---------- events & sources sheet ---------- */
const ADMIN = "kashiai.admin";
const categoryIcon = {
  religious: "🪔",
  cultural: "🎭",
  music: "🎶",
  food: "🍲",
  fair: "🎡",
  exhibition: "🖼️",
  sports: "🏏",
  other: "✨",
};
let adminKey = "";
try {
  adminKey = localStorage.getItem(ADMIN) || "";
} catch {
  adminKey = "";
}

function closeLibrary() {
  $("library").hidden = true;
  $("source-toggle").focus();
}
function showTab(name) {
  ["events", "sources"].forEach((t) => {
    $(`tab-${t}`).setAttribute("aria-selected", String(t === name));
    $(`panel-${t}`).hidden = t !== name;
  });
  if (name === "sources") loadSources();
}
$("tab-events").addEventListener("click", () => showTab("events"));
$("tab-sources").addEventListener("click", () => showTab("sources"));

async function loadSources() {
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
}

const todayIST = () =>
  new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" });
function eventDate(start) {
  // Date-only strings are calendar days in Varanasi; never shift them by timezone.
  const day = start.length === 10 ? new Date(start + "T00:00:00+05:30") : new Date(start);
  const opts = { timeZone: "Asia/Kolkata" };
  return {
    day: day.toLocaleDateString("en-IN", { ...opts, day: "numeric" }),
    month: day.toLocaleDateString("en-IN", { ...opts, month: "short" }),
    full:
      start.length === 10
        ? day.toLocaleDateString("en-IN", { ...opts, weekday: "short", day: "numeric", month: "short", year: "numeric" })
        : day.toLocaleString("en-IN", { ...opts, weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }),
    iso: day.toLocaleDateString("en-CA", opts),
  };
}

function eventCard(ev) {
  const when = eventDate(ev.start);
  const card = element("article", null, "event-card");
  const date = element("div", null, "event-date");
  date.append(element("b", when.day), element("span", when.month));
  const body = element("div", null, "event-body");
  const title = element("h3", null);
  title.append(element("span", categoryIcon[ev.category] || "✨", "event-emoji"), document.createTextNode(ev.name));
  body.append(title);
  const metaBits = [when.full];
  if (ev.end) metaBits.push("until " + eventDate(ev.end).full);
  if (ev.venue) metaBits.push(ev.venue);
  body.append(element("small", metaBits.join(" · ")));
  if (ev.description) body.append(element("p", ev.description));
  const actions = element("div", null, "event-actions");
  const askBtn = element("button", "Ask KashiAI ↗", "ghost");
  askBtn.type = "button";
  askBtn.addEventListener("click", () => {
    closeLibrary();
    ask(`Tell me about ${ev.name}`);
  });
  actions.append(askBtn);
  if (ev.source_url) actions.append(safeLink(ev.source_url, "Link", "ghost"));
  if (ev.editable && adminKey) {
    const del = element("button", "Delete", "ghost danger");
    del.type = "button";
    del.addEventListener("click", async () => {
      if (!confirm(`Delete “${ev.name}”?`)) return;
      del.disabled = true;
      try {
        const res = await fetch(API + "/api/events/" + encodeURIComponent(ev.id), {
          method: "DELETE",
          headers: { "X-Admin-Key": adminKey },
        });
        if (!res.ok) throw new Error(await errorText(res));
        loadEvents();
      } catch (e) {
        del.disabled = false;
        alert(e.message);
      }
    });
    actions.append(del);
  }
  body.append(actions);
  card.append(date, body);
  return card;
}

async function errorText(res) {
  try {
    const body = await res.json();
    if (typeof body.detail === "string") return body.detail;
    if (Array.isArray(body.detail) && body.detail[0])
      return String(body.detail[0].msg || "Please check the form.").replace(/^Value error, /, "");
  } catch {
    /* fall through */
  }
  return "Something went wrong. Please try again.";
}

async function loadEvents() {
  const list = $("event-list");
  list.textContent = "Loading events…";
  try {
    const data = await request("/api/events");
    $("add-event-toggle").hidden = !data.editing_enabled;
    const today = todayIST();
    const upcoming = data.events.filter((e) => eventDate(e.end || e.start).iso >= today);
    const past = data.events.filter((e) => eventDate(e.end || e.start).iso < today).reverse();
    const children = [];
    if (data.error) children.push(element("p", data.error, "note"));
    children.push(element("h4", upcoming.length ? "Coming up" : "Nothing scheduled yet", "event-heading"));
    upcoming.forEach((e) => children.push(eventCard(e)));
    if (!upcoming.length)
      children.push(
        element(
          "p",
          data.editing_enabled
            ? "Add the next ghat aarti, concert or mela so KashiAI can tell people about it."
            : "Check back soon for upcoming events.",
          "freshness",
        ),
      );
    if (past.length) {
      const details = element("details", null, "past-events");
      details.append(element("summary", `Past events (${past.length})`));
      past.forEach((e) => details.append(eventCard(e)));
      children.push(details);
    }
    list.replaceChildren(...children);
  } catch {
    list.textContent = "Events aren't available right now. Please try again.";
  }
}

const form = $("event-form");
function toggleForm(open) {
  form.hidden = !open;
  $("add-event-toggle").hidden = open;
  $("event-form-msg").textContent = "";
  if (open) {
    form.elements.adminKey.value = adminKey;
    form.elements.name.focus();
  }
}
$("add-event-toggle").addEventListener("click", () => toggleForm(true));
$("event-cancel").addEventListener("click", () => toggleForm(false));
form.addEventListener("submit", async (e) => {
  e.preventDefault();
  const f = form.elements;
  const msg = $("event-form-msg");
  if (!f.name.value.trim() || !f.startDate.value || !f.adminKey.value) {
    msg.textContent = "Name, start date and admin key are required.";
    return;
  }
  const payload = {
    name: f.name.value.trim(),
    category: f.category.value,
    start: f.startDate.value + (f.startTime.value ? "T" + f.startTime.value : ""),
    end: f.endDate.value || null,
    venue: f.venue.value.trim(),
    description: f.description.value.trim(),
    source_url: f.source_url.value.trim(),
  };
  $("event-save").disabled = true;
  msg.textContent = "Saving…";
  try {
    const res = await fetch(API + "/api/events", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Admin-Key": f.adminKey.value },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(60000),
    });
    if (!res.ok) throw new Error(await errorText(res));
    adminKey = f.adminKey.value;
    try {
      if (f.remember.checked) localStorage.setItem(ADMIN, adminKey);
      else localStorage.removeItem(ADMIN);
    } catch {
      /* Storage unavailable; the key stays for this session only. */
    }
    form.reset();
    toggleForm(false);
    $("add-event-toggle").hidden = false;
    loadEvents();
  } catch (err) {
    msg.textContent =
      err.name === "TimeoutError" ? "The server took too long. Please try again." : err.message;
  } finally {
    $("event-save").disabled = false;
  }
});

$("source-toggle").addEventListener("click", () => {
  $("library").hidden = false;
  $("source-close").focus();
  showTab("events");
  loadEvents();
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
