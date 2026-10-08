// History: streaks, a week/month dot chart, a month calendar, and a day view where you can
// remove a drink or add one you forgot.
import { store, todayKey, dayBefore } from "./store.js";
import { state, drinks, reload, $ } from "./state.js";
import { streak, bestStreak, effective } from "./goal.js";
import { openSheet } from "./sheets.js";
import { haptic } from "./feedback.js";

const NS = "http://www.w3.org/2000/svg";
let range = "w";
let openDay = null;

const mk = (svg, tag, attrs) => {
  const e = document.createElementNS(NS, tag);
  for (const k in attrs) e.setAttribute(k, attrs[k]);
  svg.appendChild(e);
  return e;
};

function drawHistory() {
  const H = state.hist;
  const s = streak(H, state.today, dayBefore), best = bestStreak(H);
  $("#streak").textContent = s;
  $("#streakNote").textContent = `${s === 1 ? "day" : "days"} in a row at your goal · best ${best}`;

  // Week or month of dot columns
  const n = range === "w" ? 7 : 30, days = [];
  for (let i = n - 1; i >= 0; i--) { const d = new Date(); d.setDate(d.getDate() - i); days.push(todayKey(d)); }
  const vals = days.map((k) => H[k] || null);
  const max = Math.max(...days.map((k) => H[k]?.g || 0), ...vals.map((v) => (v ? v.e : 0)), 1);
  const chart = $("#chart");
  chart.innerHTML = "";
  const cw = 360 / n, rows = 22, r = Math.min(cw * 0.3, 5);
  days.forEach((k, i) => {
    const v = vals[i], filled = Math.round(((v ? v.e : 0) / max) * rows), cx = cw * i + cw / 2;
    const g = mk(chart, "g", { role: "button", tabindex: 0, "aria-label": `${k}: ${v ? v.e : 0} ml` });
    for (let j = 0; j < rows; j++) mk(g, "circle", { cx, cy: 176 - j * (160 / rows), r, fill: j < filled ? "#f3f3ef" : "#1f1f1f" });
    mk(g, "rect", { x: cw * i, y: 0, width: cw, height: 205, fill: "transparent" });
    g.onclick = () => showDay(k);
    if (n === 7) mk(chart, "text", { x: cx, y: 200, "text-anchor": "middle", "font-size": 11, fill: "#8d8d88" })
      .textContent = new Date(k + "T12:00").toLocaleDateString(undefined, { weekday: "short" });
  });
  const goalLine = H[state.today]?.g || 0;
  if (goalLine) { const gy = 176 - (160 * goalLine) / max; mk(chart, "line", { x1: 0, x2: 360, y1: gy, y2: gy, stroke: "#f3f3ef88", "stroke-dasharray": "3 5" }); }
  const rec = vals.filter(Boolean);
  $("#summary").textContent = rec.length
    ? `Average ${Math.round(rec.reduce((a, v) => a + v.e, 0) / rec.length)} ml a day, goal met ${rec.filter((v) => v.e >= v.g).length} of ${rec.length} days, best ${Math.max(...rec.map((v) => v.e))} ml. Dashed line is today's goal.`
    : "No data yet. Your history builds up from today.";

  // Month calendar
  const now = new Date(), y = now.getFullYear(), m = now.getMonth();
  const offset = (new Date(y, m, 1).getDay() + 6) % 7, dim = new Date(y, m + 1, 0).getDate(), cl = 360 / 7;
  $("#monthName").textContent = now.toLocaleDateString(undefined, { month: "long", year: "numeric" });
  const cal = $("#cal");
  cal.innerHTML = "";
  "MTWTFSS".split("").forEach((c, i) => (mk(cal, "text", { x: cl * i + cl / 2, y: 12, "text-anchor": "middle", "font-size": 11, fill: "#8d8d88" }).textContent = c));
  for (let d = 1; d <= dim; d++) {
    const idx = offset + d - 1, cx = (idx % 7) * cl + cl / 2, cy = 36 + Math.floor(idx / 7) * 38;
    const key = todayKey(new Date(y, m, d)), v = H[key], fr = v ? v.e / v.g : null, future = key > state.today;
    const [fill, rr] = future ? ["#141414", 3] : fr === null ? ["#1c1c1c", 4] : fr < 0.5 ? ["#4a4a4a", 6] : fr < 0.8 ? ["#8a8a86", 7] : fr < 1 ? ["#cfcfca", 8] : ["#f3f3ef", 9];
    const g = mk(cal, "g", future ? {} : { role: "button", tabindex: 0, "aria-label": `${key}: ${v ? v.e : 0} ml` });
    mk(g, "circle", { cx, cy, r: rr, fill });
    if (key === state.today) mk(g, "circle", { cx, cy, r: 13, fill: "none", stroke: "#f3f3ef", opacity: 0.7 });
    mk(g, "rect", { x: cx - cl / 2, y: cy - 19, width: cl, height: 38, fill: "transparent" });
    if (!future) g.onclick = () => showDay(key);
  }
}

async function showDay(key) {
  openDay = key;
  openSheet("#dayView");
  await drawDay();
}

async function drawDay() {
  const key = openDay;
  const list = key === state.today ? state.entries : await store.day(key);
  $("#dayTitle").textContent = new Date(key + "T12:00").toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" });
  const g = state.hist[key]?.g;
  $("#daySummary").textContent = list.length
    ? `${list.length} ${list.length === 1 ? "drink" : "drinks"} · ${effective(list)} ml${g ? ` of ${g} ml` : ""}`
    : "Nothing logged on this day.";
  const box = $("#dayList");
  box.innerHTML = "";
  for (const e of list) {
    const row = document.createElement("div");
    row.className = "item";
    row.innerHTML = `<span class="w"><span></span><small></small></span><button class="x">✕</button>`;
    row.querySelector(".w > span").textContent = `${e.ml} ml ${e.n || "Water"}`;
    row.querySelector("small").textContent = new Date(e.t).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    const x = row.querySelector(".x");
    x.setAttribute("aria-label", `Remove ${e.ml} ml ${e.n || "Water"}`);
    x.onclick = async () => { await store.remove(key, e.id); haptic("undo"); await reload(); drawDay(); };
    box.appendChild(row);
  }
}

export function initHistory() {
  const sel = $("#pastDrink");
  const fill = () => { sel.innerHTML = ""; drinks().forEach((d, i) => sel.add(new Option(d.n, i))); };
  $("#openHistory").onclick = () => { openSheet("#history"); drawHistory(); };
  $("#rangeWeek").onclick = () => { range = "w"; $("#rangeWeek").classList.add("on"); $("#rangeMonth").classList.remove("on"); drawHistory(); };
  $("#rangeMonth").onclick = () => { range = "m"; $("#rangeMonth").classList.add("on"); $("#rangeWeek").classList.remove("on"); drawHistory(); };
  $("#pastAdd").onclick = async () => {
    const ml = +$("#pastMl").value, d = drinks()[+sel.value] || drinks()[0];
    const [hh, mm] = ($("#pastTime").value || "12:00").split(":").map(Number);
    if (!(ml > 0 && ml <= 3000)) { $("#pastMl").setAttribute("aria-invalid", "true"); return; }
    $("#pastMl").removeAttribute("aria-invalid");
    const t = new Date(openDay + "T00:00"); t.setHours(hh, mm, 0, 0);
    await store.add({ ml, f: d.f, c: d.c, n: d.n, t: t.getTime() });
    haptic("click");
    await reload();
    drawDay();
  };
  fill();
  // Keep open sheets fresh when data changes (e.g. a widget tap while viewing history)
  const refresh = () => {
    fill();
    if (!$("#history").hidden) drawHistory();
    if (!$("#dayView").hidden && openDay) drawDay();
  };
  return refresh;
}

