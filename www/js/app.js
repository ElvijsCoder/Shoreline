// Shoreline's home screen and start-up.
import { store, todayKey, newId, migrateOldVersion } from "./store.js";
import { state, drinks, currentGoal, onRender, render, reload, saveProfile, syncGoal, $, $$ } from "./state.js";
import { effective, caffeine, caffeineCap, adjFromChecks, activeCaffeine } from "./goal.js";
import { rollTo } from "./rolling.js";
import { initRing, setRing } from "./ring.js";
import { initShore, setTide, splash, celebrate } from "./shore.js";
import { tip } from "./tips.js";
import { playDrop, playWash, haptic } from "./feedback.js";
import { initHistory } from "./history.js";
import { initSettings, openSettings } from "./settings.js";
import { startWelcome } from "./welcome.js";
import { updateWeather } from "./weather.js";

let cleared = null; // what "hold to clear" removed, so Restore can bring it back

// --- Drawing the home screen ------------------------------------------------------------

function drawHome() {
  const p = state.profile, g = currentGoal(), e = effective(state.entries), pct = e / g;
  const caf = caffeine(state.entries), cap = caffeineCap(p), active = activeCaffeine(state.entries);

  setRing({ water: pct, total: caf / cap, active: active / cap, over: Math.max(caf - cap, 0) / cap });
  setTide(pct);
  rollTo($("#pct"), `${Math.round(pct * 100)}%`);
  rollTo($("#left"), e >= g ? "goal reached" : `${(g - e).toLocaleString()} ml left`);
  const cafEl = $("#caf");
  cafEl.textContent = caf > 0 ? `caffeine ${Math.round(caf)} / ${cap} mg\n~${Math.round(active)} mg still active` : "";
  cafEl.classList.toggle("over", caf > cap);
  $("#fact").textContent = tip({ p: pct, caf, entries: state.entries, profile: p, goal: g });
  $("#log").textContent = state.entries.length
    ? `${state.entries.length} ${state.entries.length === 1 ? "drink" : "drinks"} · ${e.toLocaleString()} of ${g.toLocaleString()} ml`
    : "Nothing logged yet today";

  // Morning check: once a day, before noon
  $("#check").hidden = !(new Date().getHours() < 12 && !(p.ck || []).some((x) => x.d === state.today));

  // Cup buttons follow your cup sizes
  const cups = $("#cups");
  const sizes = p.cups.join(",");
  if (cups.dataset.sizes !== sizes) {
    cups.dataset.sizes = sizes;
    cups.innerHTML = "";
    for (const ml of p.cups) {
      const b = document.createElement("button");
      b.className = "btn";
      b.textContent = `+${ml}`;
      b.setAttribute("aria-label", `Add ${ml} ml`);
      b.onclick = () => add(ml);
      cups.appendChild(b);
    }
    const custom = document.createElement("button");
    custom.className = "btn";
    custom.textContent = "Custom";
    custom.onclick = () => { const r = $("#customRow"); r.hidden = !r.hidden; if (!r.hidden) $("#customMl").focus(); };
    cups.appendChild(custom);
  }
  $("#bottleOpen").hidden = !(p.bottle > 0);
  $("#bottleOpen").textContent = `Bottle · ${p.bottle} ml`;
  if (!(p.bottle > 0)) $("#bottleRow").hidden = true;
  $("#lastCustom").hidden = !p.lc;
  $("#lastCustom").textContent = `+${p.lc} ml (last custom)`;
  $("#undo").textContent = !state.entries.length && cleared ? "Restore" : "Undo (hold to clear)";

  // Drink picker follows your own drinks
  const sel = $("#drink"), names = drinks().map((d) => d.n).join("|");
  if (sel.dataset.names !== names) {
    const keep = sel.value;
    sel.dataset.names = names;
    sel.innerHTML = "";
    drinks().forEach((d, i) => sel.add(new Option(d.n, i)));
    if (keep && +keep < drinks().length) sel.value = keep;
  }
}

// --- Logging -----------------------------------------------------------------------------

async function add(ml) {
  if (!(ml > 0 && ml <= 3000)) return;
  ensureToday();
  const d = drinks()[+$("#drink").value] || drinks()[0];
  const before = effective(state.entries) >= currentGoal();
  const entry = { id: newId(), t: Date.now(), ml, f: d.f, c: d.c, n: d.n };
  state.entries = [...state.entries, entry]; // show it straight away…
  cleared = null;
  playDrop();
  if (!before && effective(state.entries) >= currentGoal()) {
    celebrate(); // the goal wave
    setTimeout(playWash, 120);
    haptic("goal");
  } else {
    splash();
    haptic("click");
  }
  render();
  try {
    await store.add(entry); // …then save it
    if (!store.native) await reload(); // the phone reloads by itself when the store changes
  } catch { await reload(); }
}

async function undo() {
  if (!state.entries.length && cleared) {
    await store.restoreDay(state.today, cleared);
    cleared = null;
    haptic("tick");
  } else if (state.entries.length) {
    await store.undo();
    haptic("undo");
  }
  await reload();
}

async function clearToday() {
  if (!state.entries.length) return;
  cleared = await store.clearDay(state.today);
  haptic("clear");
  await reload();
}

// --- Keeping up with the clock and with changes made elsewhere ----------------------

/** A new day started while the app was open: load the new (empty) day. */
function ensureToday() {
  if (todayKey() !== state.today) { cleared = null; reload().then(syncGoal); }
}

async function onResume() {
  await reload(); // picks up widget, tile and notification drinks, and a new day
  await syncGoal();
  updateWeather(false, (m) => { $("#weatherStatus").textContent = m; });
}

// --- Start-up ------------------------------------------------------------------------------

function wireHome() {
  $("#customAdd").onclick = () => {
    const v = +$("#customMl").value;
    if (!(v > 0 && v <= 3000)) { $("#customMl").setAttribute("aria-invalid", "true"); return; }
    $("#customMl").removeAttribute("aria-invalid");
    saveProfile({ lc: v });
    add(v);
    $("#customMl").value = "";
  };
  $("#customMl").onkeydown = (e) => { if (e.key === "Enter") $("#customAdd").click(); };
  $("#lastCustom").onclick = () => add(state.profile.lc);

  // Bottle mode: drag or tap ¼ ½ ¾ Full, then Add
  const slider = $("#bottleSlider");
  const showAmount = () => { $("#bottleAmount").textContent = `${slider.value} ml`; };
  $("#bottleOpen").onclick = () => {
    const r = $("#bottleRow");
    r.hidden = !r.hidden;
    if (!r.hidden) { slider.max = state.profile.bottle; slider.value = Math.round(state.profile.bottle / 2 / 25) * 25; showAmount(); }
  };
  slider.oninput = () => { showAmount(); haptic("tick"); };
  $$("[data-part]").forEach((b) => (b.onclick = () => {
    slider.value = Math.round((state.profile.bottle * +b.dataset.part) / 25) * 25;
    showAmount();
  }));
  $("#bottleAdd").onclick = () => {
    const v = +slider.value;
    if (v > 0) { add(v); $("#bottleRow").hidden = true; }
  };

  // Undo: tap to undo, hold to clear the day (and tap again to restore)
  const u = $("#undo");
  let holdTimer = 0, held = false;
  u.onpointerdown = () => { held = false; u.classList.add("glow"); holdTimer = setTimeout(() => { held = true; clearToday(); }, 700); };
  const release = () => { clearTimeout(holdTimer); u.classList.remove("glow"); };
  u.onpointerup = u.onpointerleave = u.onpointercancel = release;
  u.oncontextmenu = (e) => e.preventDefault();
  u.onclick = () => { if (held) { held = false; return; } undo(); };

  $$("[data-urine]").forEach((b) => (b.onclick = () => {
    const ck = [...(state.profile.ck || []), { d: state.today, v: +b.dataset.urine }].slice(-7);
    haptic("tick");
    saveProfile({ ck, adj: adjFromChecks(ck) });
  }));

  // Every button glows briefly when pressed, like the widget buttons
  document.addEventListener("click", (e) => {
    const b = e.target.closest(".btn");
    if (b && b.id !== "undo") { b.classList.add("glow"); setTimeout(() => b.classList.remove("glow"), 380); }
  });
}

function openTarget(target) {
  if (target === "settings:you") openSettings("you");
  if (target === "history") $("#openHistory").click();
}

async function boot() {
  initRing($("#ring"));
  initShore();
  wireHome();
  const refreshHistory = initHistory();
  const refreshSettings = initSettings();
  onRender(drawHome);
  onRender(refreshHistory);
  onRender(() => { if (!$("#settings").hidden) refreshSettings(); });

  await migrateOldVersion();
  await reload();
  await syncGoal();

  store.onChange(() => reload());
  store.onOpen(openTarget);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) onResume(); });
  // Once a minute: catch a new day, and let caffeine wear off and the pace tip move on.
  setInterval(() => { ensureToday(); render(); }, 60_000);

  if (!state.profile.onboarded) {
    startWelcome(() => { render(); syncGoal(); });
  } else {
    openTarget(await store.launchTarget());
    updateWeather(false, (m) => { $("#weatherStatus").textContent = m; });
  }
}

boot();
