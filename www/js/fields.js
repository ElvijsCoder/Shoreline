// Every setting is defined once here and drawn into Settings and the welcome screens.
// Values are checked against min/max so a typo can't turn the goal into nonsense.
import { state, saveProfile } from "./state.js";

const thisYear = new Date().getFullYear();

export const FIELDS = {
  sex: { label: "Sex", type: "select", options: [["m", "Male"], ["f", "Female"]] },
  by: { label: "Birth year", type: "number", min: 1920, max: thisYear - 12 },
  kg: { label: "Weight (kg)", type: "number", min: 30, max: 250 },
  bf: { label: "Body fat % (optional)", type: "number", min: 0, max: 60, optional: true },
  act: { label: "Activity", type: "select", num: true, options: [[0, "Mostly sitting"], [1, "Light movement"], [2, "Active"], [3, "Athlete / hard training"]] },
  pr: { label: "Pregnancy / nursing", type: "select", num: true, options: [[0, "No"], [1, "Late pregnancy"], [2, "Nursing"]], show: () => state.profile.sex === "f" },
  limit: { label: "Fluid limit from your doctor (ml, optional)", type: "number", min: 0, max: 5000, optional: true },
  temp: { label: "Temperature (°C)", type: "number", min: -30, max: 50 },
  rh: { label: "Humidity (%)", type: "number", min: 0, max: 100 },
  alt: { label: "Altitude (m)", type: "number", min: 0, max: 5000 },
  wake: { label: "Wake up", type: "time" },
  slp: { label: "Sleep", type: "time" },
  iv: { label: "Remind every", type: "select", num: true, options: [[60, "1 hour"], [90, "1.5 hours"], [120, "2 hours"]] },
  cut: { label: "No caffeine after", type: "time" },
  cup0: { label: "Small cup (ml)", type: "number", min: 20, max: 3000, cup: 0 },
  cup1: { label: "Middle cup (ml)", type: "number", min: 20, max: 3000, cup: 1 },
  cup2: { label: "Large cup (ml)", type: "number", min: 20, max: 3000, cup: 2 },
  autoWeather: { label: "Automatic weather", hint: "Updates temperature and humidity a few times a day", type: "toggle" },
  remind: { label: "Drink reminders", hint: "Only while you're awake and behind pace", type: "toggle" },
  weightRemind: { label: "Weekly weight check", hint: "A Monday nudge to keep the goal accurate", type: "toggle" },
};

export const GROUPS = {
  you: ["sex", "by", "kg", "bf", "act", "pr", "limit"],
  weatherToggle: ["autoWeather"],
  weather: ["temp", "rh", "alt"],
  day: ["wake", "slp", "iv", "cut"],
  reminders: ["remind", "weightRemind"],
  cups: ["cup0", "cup1", "cup2"],
  welcomeYou: ["sex", "by", "kg", "act", "pr"],
  welcomeDay: ["wake", "slp", "iv"],
};

const value = (k) => (FIELDS[k].cup !== undefined ? state.profile.cups[FIELDS[k].cup] : state.profile[k]);

function set(k, v) {
  const f = FIELDS[k];
  if (f.cup !== undefined) {
    const cups = [...state.profile.cups];
    cups[f.cup] = v;
    saveProfile({ cups });
  } else saveProfile({ [k]: v });
}

function control(k) {
  const f = FIELDS[k];
  if (f.type === "toggle") {
    const b = document.createElement("button");
    b.className = "tog";
    b.setAttribute("role", "switch");
    b.innerHTML = `<span class="t"><span></span><small></small></span><span class="s"><i></i><span></span></span>`;
    b.querySelector(".t > span").textContent = f.label;
    b.querySelector("small").textContent = f.hint || "";
    const sync = () => {
      b.setAttribute("aria-checked", String(!!state.profile[k]));
      b.querySelector(".s > span").textContent = state.profile[k] ? "ON" : "OFF";
    };
    b.onclick = () => { set(k, !state.profile[k]); sync(); };
    b.sync = sync;
    sync();
    return b;
  }
  const label = document.createElement("label");
  label.textContent = f.label;
  let el;
  if (f.type === "select") {
    el = document.createElement("select");
    for (const [v, text] of f.options) el.add(new Option(text, v));
  } else {
    el = document.createElement("input");
    el.type = f.type;
    if (f.type === "number") { el.min = f.min; el.max = f.max; el.inputMode = "numeric"; }
    if (f.optional) el.placeholder = "optional";
  }
  el.dataset.key = k;
  label.appendChild(el);
  label.sync = () => {
    if (document.activeElement === el) return; // don't overwrite what you're typing
    const v = value(k);
    el.value = f.optional && !v ? "" : v ?? "";
    if (f.show) label.hidden = !f.show();
    el.removeAttribute("aria-invalid");
  };
  el.onchange = () => {
    if (f.type === "number") {
      if (el.value === "" && f.optional) { set(k, 0); return; }
      const n = +el.value;
      if (el.value === "" || !Number.isFinite(n) || n < f.min || n > f.max) {
        el.setAttribute("aria-invalid", "true");
        el.value = value(k) || ""; // put back the last good value
        return;
      }
      set(k, n);
    } else if (f.type === "time") {
      if (/^\d{2}:\d{2}$/.test(el.value)) set(k, el.value); else el.value = value(k);
    } else {
      set(k, f.num ? +el.value : el.value);
    }
    if (k === "sex") document.querySelectorAll("[data-fields]").forEach((g) => g.syncAll?.());
  };
  label.sync();
  return label;
}

/** Draws every [data-fields="group"] container under `root`. Call `.syncAll()` to refresh. */
export function mountFields(root) {
  for (const box of root.querySelectorAll("[data-fields]")) {
    if (box.syncAll) continue;
    const parts = GROUPS[box.dataset.fields].map(control);
    parts.forEach((p) => box.appendChild(p));
    box.syncAll = () => parts.forEach((p) => p.sync());
  }
}

export const syncFields = (root) => root.querySelectorAll("[data-fields]").forEach((b) => b.syncAll?.());
