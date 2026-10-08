// Settings: you, weather, your day and reminders, cups and drinks, backup, and how the goal
// is worked out.
import { store, fromV1 } from "./store.js";
import { state, saveProfile, reload, currentGoal, $ } from "./state.js";
import { turnover, caffeineCap, pregnancyExtra, CAFFEINE_HALF_LIFE_H } from "./goal.js";
import { mountFields, syncFields } from "./fields.js";
import { openSheet } from "./sheets.js";
import { updateWeather } from "./weather.js";

const root = () => $("#settings");
const say = (sel) => (m) => { $(sel).textContent = m; };

function explain() {
  const p = state.profile, g = currentGoal(), extra = pregnancyExtra(p);
  $("#why").textContent =
    `Goal ${g} ml. Based on a water turnover of about ${(turnover(p) / 1000).toFixed(1)} L a day from the 2022 Yamada et al. Science equation. `
    + "About 85% of turnover is water you take in, and about a fifth of that comes from food, so the rest is your drinking goal. "
    + (extra ? `Pregnancy or nursing adds about ${extra} ml of water a day (EFSA). ` : "")
    + "The equation explains just under half of person-to-person variation, so treat it as a starting point. "
    + (p.adj ? `Your morning checks adjust it by ${Math.round(p.adj * 100)}%. ` : "")
    + (p.limit > 0 ? `Your fluid limit of ${p.limit} ml caps it. ` : "")
    + `Caffeine limit ${caffeineCap(p)} mg a day (EFSA 2015: 5.7 mg per kg of body weight, up to 400 mg, or 200 mg in pregnancy and nursing). `
    + `The bright inner dots are caffeine still active in your body, worked out with a typical half-life of ${CAFFEINE_HALF_LIFE_H} hours; `
    + "it varies between people (roughly 3–7 hours) and is longer in pregnancy, so treat it as a guide.";
}

function notifStatus() {
  const on = state.notifications;
  $("#notifStatus").textContent = !store.native ? "" : on
    ? "Notifications are allowed."
    : "Notifications are off, so reminders can't appear.";
  $("#notifAllow").hidden = !store.native || on;
}

function drinkList() {
  const box = $("#drinkList");
  box.innerHTML = "";
  (state.profile.drinks || []).forEach((d, i) => {
    const row = document.createElement("div");
    row.className = "item";
    row.innerHTML = `<span class="w"><span></span><small></small></span><button class="x">✕</button>`;
    row.querySelector(".w > span").textContent = d.n;
    row.querySelector("small").textContent = d.c ? `${d.c} mg caffeine per 100 ml` : "No caffeine";
    row.querySelector(".x").setAttribute("aria-label", `Remove ${d.n}`);
    row.querySelector(".x").onclick = () => saveProfile({ drinks: state.profile.drinks.filter((_, j) => j !== i) });
    box.appendChild(row);
  });
}

async function exportBackup() {
  const status = say("#backupStatus");
  try {
    const data = JSON.stringify(await store.exportAll());
    const F = window.Capacitor?.Plugins?.Filesystem, Share = window.Capacitor?.Plugins?.Share;
    if (F && Share) {
      const path = `shoreline-backup-${state.today}.json`;
      await F.writeFile({ path, data, directory: "CACHE", encoding: "utf8" });
      const { uri } = await F.getUri({ path, directory: "CACHE" });
      await Share.share({ title: "Shoreline backup", files: [uri] });
      status("Backup ready to save or send.");
    } else {
      await navigator.clipboard.writeText(data);
      status("Backup copied to the clipboard.");
    }
  } catch (e) { status(`Export failed: ${e?.message || e}`); }
}

function importBackup(file) {
  const status = say("#backupStatus");
  const reader = new FileReader();
  reader.onload = async () => {
    try {
      const d = JSON.parse(reader.result);
      // Version 2 is this app's format; version 1 ({S, H}) came from the first Shoreline.
      const data = d.v === 2 ? d : d.S || d.H ? fromV1(d.S, d.H) : null;
      if (!data) throw new Error("this isn't a Shoreline backup");
      const days = Object.keys(data.hist || {}).length;
      await store.importAll(data);
      await reload();
      syncFields(root());
      status(`Imported ${days} days of history and your settings.`);
    } catch (e) { status(`Import failed: ${e.message}`); }
  };
  reader.readAsText(file);
}

// Health Connect: a switch that asks Android for permission the first time it's turned on.
async function healthStatus() {
  const st = await store.hcStatus();
  const tog = $("#hcToggle"), note = $("#hcStatus");
  tog.hidden = !st.available;
  tog.setAttribute("aria-checked", String(st.on));
  tog.querySelector(".s > span").textContent = st.on ? "ON" : "OFF";
  note.textContent = !st.available
    ? (store.native ? "Health Connect needs Android 14 or newer." : "Health Connect works in the phone app.")
    : st.on ? "Sending your water to Health Connect." : "";
}

async function toggleHealth() {
  const st = await store.hcStatus();
  if (st.on) {
    await store.hcDisable();
  } else {
    const ok = await store.hcEnable().catch(() => false);
    if (!ok) $("#hcStatus").textContent = "Permission wasn't given. You can allow it in Health Connect's settings and try again.";
  }
  await reload(); // the profile's on/off flag changed natively
  await healthStatus();
  if (!st.on && (await store.hcStatus()).on) $("#hcStatus").textContent = "On. Your last 30 days were sent, and new drinks follow automatically.";
}

export function initSettings() {
  $("#hcToggle").onclick = toggleHealth;
  mountFields(root());
  $("#openSettings").onclick = () => openSettings();
  $("#weatherNow").onclick = () => updateWeather(true, say("#weatherStatus"));
  $("#notifAllow").onclick = async () => { state.notifications = await store.requestNotifications(); notifStatus(); };
  $("#newDrinkAdd").onclick = () => {
    const n = $("#newDrinkName").value.trim(), c = +$("#newDrinkCaf").value || 0;
    if (!n) { $("#newDrinkName").setAttribute("aria-invalid", "true"); return; }
    $("#newDrinkName").removeAttribute("aria-invalid");
    saveProfile({ drinks: [...(state.profile.drinks || []), { n: n.slice(0, 24), f: 1, c: Math.min(500, Math.max(0, c)) }] });
    $("#newDrinkName").value = "";
    $("#newDrinkCaf").value = "0";
  };
  $("#exportBtn").onclick = exportBackup;
  $("#importBtn").onclick = () => $("#importFile").click();
  $("#importFile").onchange = (e) => { const f = e.target.files[0]; if (f) importBackup(f); e.target.value = ""; };
  return () => { syncFields(root()); explain(); notifStatus(); drinkList(); };
}

/** Opens Settings, optionally at the YOU section with the weight field focused. */
export function openSettings(section) {
  openSheet("#settings");
  syncFields(root());
  explain();
  notifStatus();
  drinkList();
  healthStatus();
  if (section === "you") {
    $("#you").scrollIntoView();
    root().querySelector('[data-key="kg"]')?.focus();
  }
  if (section === "health") $("#health").scrollIntoView();
}
