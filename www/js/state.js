// The app's in-memory state and the few helpers every screen needs.
import { store, todayKey, DEFAULT_PROFILE } from "./store.js";
import { goal, adjFromChecks } from "./goal.js";

export const state = {
  today: todayKey(),
  entries: [], // today's drinks, oldest first
  hist: {}, // date → { e, g }
  profile: { ...DEFAULT_PROFILE },
  notifications: false,
};

export const BUILTIN_DRINKS = [
  { n: "Water", f: 1, c: 0 }, { n: "Coffee", f: 1, c: 40 }, { n: "Tea", f: 1, c: 20 },
  { n: "Diet cola", f: 1, c: 12 }, { n: "Milk", f: 1.1, c: 0 }, { n: "Juice", f: 1.05, c: 0 },
];

/** Built-in drinks plus your own. */
export const drinks = () => [...BUILTIN_DRINKS, ...(state.profile.drinks || []).map((d) => ({ f: 1, ...d }))];

export const currentGoal = () => goal(state.profile);

const listeners = new Set();
/** Screens subscribe to re-draw after any change. */
export const onRender = (fn) => listeners.add(fn);
export const render = () => listeners.forEach((fn) => fn());

/** Reloads everything from the store (after widget/tile changes, resume, a new day). */
export async function reload() {
  const s = await store.load();
  state.today = s.today || todayKey();
  state.entries = s.entries || [];
  state.hist = s.hist || {};
  state.profile = { ...DEFAULT_PROFILE, ...(s.profile || {}) };
  state.notifications = !!s.notifications;
  render();
}

let saveTimer = 0;
/** Saves the profile (debounced) and keeps today's goal in sync for widgets and history. */
export function saveProfile(patch = {}) {
  Object.assign(state.profile, patch);
  state.profile.adj = adjFromChecks(state.profile.ck);
  state.profile.goal = currentGoal();
  render();
  clearTimeout(saveTimer);
  saveTimer = setTimeout(async () => {
    await store.setProfile(state.profile);
    await syncGoal();
  }, 400);
}

/** Writes today's goal if it changed, so widgets, reminders and history agree. */
export async function syncGoal() {
  const g = currentGoal();
  if (state.hist[state.today]?.g !== g) {
    state.hist[state.today] = { ...(state.hist[state.today] || { e: 0 }), g };
    await store.setGoal(state.today, g);
  }
}

export const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;
export const $ = (s) => document.querySelector(s);
export const $$ = (s) => [...document.querySelectorAll(s)];
