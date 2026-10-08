// Where Shoreline's data lives. On the phone everything goes through the native "Shore"
// plugin (one store shared with widgets, tile and notifications). In a desktop browser a
// localStorage stand-in with the same API keeps the app previewable.

const Native = window.Capacitor?.Plugins?.Shore;

export const todayKey = (d = new Date()) => d.toLocaleDateString("sv-SE");
export const dayBefore = (key) => { const d = new Date(key + "T12:00"); d.setDate(d.getDate() - 1); return todayKey(d); };
export const newId = (t = Date.now()) => `${t}-${Math.floor(Math.random() * 1e6)}`;

/** A brand-new profile. `onboarded` stays false until the welcome screens are done. */
export const DEFAULT_PROFILE = {
  kg: 75, sex: "m", by: 1990, bf: 0, act: 1, pr: 0, limit: 0,
  temp: 15, rh: 60, alt: 0, autoWeather: false, wxT: 0,
  wake: "08:00", slp: "22:30", iv: 90, cut: "14:00", remind: true, weightRemind: true,
  cups: [150, 250, 500], drinks: [], lc: 0, bottle: 0, tilt: true, recap: true,
  ck: [], adj: 0, goal: 0, onboarded: false,
};

// --- Native ---------------------------------------------------------------------------

const nativeStore = {
  native: true,
  load: () => Native.getState(),
  day: async (date) => (await Native.day({ date })).entries,
  add: (e) => Native.add(e),
  remove: (date, id) => Native.remove({ date, id }),
  undo: async () => (await Native.undo()).entry,
  clearDay: async (date) => (await Native.clearDay({ date })).entries,
  restoreDay: (date, entries) => Native.restoreDay({ date, entries }),
  setGoal: (date, goal) => Native.setGoal({ date, goal }),
  setProfile: (profile) => Native.setProfile({ profile }),
  exportAll: () => Native.exportAll(),
  importAll: (data) => Native.importAll({ data }),
  launchTarget: async () => (await Native.launchTarget()).target,
  requestNotifications: async () => (await Native.requestNotifications()).granted,
  locate: () => Native.locate(),
  haptic: (o) => Native.haptic(o).catch(() => {}),
  onChange: (cb) => Native.addListener("changed", cb),
  onOpen: (cb) => Native.addListener("open", (e) => cb(e.target)),
};

// --- Browser stand-in -------------------------------------------------------------------

const KEY = "shoreline-dev";
const read = () => { try { return JSON.parse(localStorage.getItem(KEY)) || { profile: {}, days: {}, hist: {} }; } catch { return { profile: {}, days: {}, hist: {} }; } };
const write = (d) => localStorage.setItem(KEY, JSON.stringify(d));
const eff = (list) => Math.round(list.reduce((a, e) => a + e.ml * (e.f ?? 1), 0));
const touch = (d, date) => {
  const list = d.days[date] || [];
  const g = d.hist[date]?.g || d.profile.goal || 2200;
  d.hist[date] = { e: eff(list), g };
};

const localStore = {
  native: false,
  async load() {
    const d = read(), today = todayKey();
    return { today, entries: d.days[today] || [], hist: d.hist, profile: d.profile, notifications: false };
  },
  async day(date) { return read().days[date] || []; },
  async add(e) {
    const d = read(), entry = { id: e.id || newId(e.t), t: e.t || Date.now(), ml: e.ml, f: e.f ?? 1, c: e.c || 0, n: e.n || "Water" };
    const date = todayKey(new Date(entry.t));
    d.days[date] = [...(d.days[date] || []), entry].sort((a, b) => a.t - b.t);
    touch(d, date); write(d); return entry;
  },
  async remove(date, id) { const d = read(); d.days[date] = (d.days[date] || []).filter((e) => e.id !== id); touch(d, date); write(d); },
  async undo() { const d = read(), date = todayKey(), list = d.days[date] || []; const e = list.pop(); d.days[date] = list; touch(d, date); write(d); return e; },
  async clearDay(date) { const d = read(), list = d.days[date] || []; d.days[date] = []; touch(d, date); write(d); return list; },
  async restoreDay(date, entries) {
    const d = read(), ids = new Set((d.days[date] || []).map((e) => e.id));
    d.days[date] = [...(d.days[date] || []), ...entries.filter((e) => !ids.has(e.id))].sort((a, b) => a.t - b.t);
    touch(d, date); write(d);
  },
  async setGoal(date, goal) { const d = read(); d.hist[date] = { e: eff(d.days[date] || []), g: goal }; write(d); },
  async setProfile(profile) { const d = read(); d.profile = profile; write(d); },
  async exportAll() { const d = read(); return { v: 2, profile: d.profile, days: d.days, hist: d.hist }; },
  async importAll(data) {
    const d = read();
    if (data.profile && Object.keys(data.profile).length) d.profile = data.profile;
    Object.assign(d.hist, data.hist || {});
    for (const [date, list] of Object.entries(data.days || {})) {
      const ids = new Set((d.days[date] || []).map((e) => e.id));
      d.days[date] = [...(d.days[date] || []), ...list.filter((e) => !ids.has(e.id))].sort((a, b) => a.t - b.t);
    }
    write(d);
  },
  async launchTarget() { return null; },
  async requestNotifications() { return false; },
  locate: () => new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(
    (p) => resolve({ lat: Math.round(p.coords.latitude * 100) / 100, lon: Math.round(p.coords.longitude * 100) / 100 }),
    reject, { timeout: 20000, maximumAge: 3600000 })),
  async haptic(o) { try { navigator.vibrate?.(o.pattern || (o.type === "heavy" ? 40 : 15)); } catch { /* no vibration */ } },
  onChange() {},
  onOpen() {},
};

export const store = Native ? nativeStore : localStore;

// --- Old data (version 1) -----------------------------------------------------------------

/**
 * Converts the first version's data ({S, H} in localStorage or a v1 backup file) into a v2
 * backup: profile, timestamped drinks for the day it held, and the daily history.
 */
export function fromV1(S = {}, H = {}) {
  const pick = ["kg", "sex", "by", "bf", "act", "temp", "rh", "alt", "pr", "wake", "slp", "cut", "iv", "lc", "adj", "ck", "wxT"];
  const profile = { ...DEFAULT_PROFILE, onboarded: true, autoWeather: true }; // v1 always fetched weather
  for (const k of pick) if (S[k] !== undefined && S[k] !== "") profile[k] = S[k];
  profile.bf = +profile.bf || 0;
  const days = {};
  if (S.day && Array.isArray(S.log) && S.log.length) {
    const noon = new Date(S.day + "T12:00").getTime();
    days[S.day] = S.log
      .filter((e) => e && e.ml > 0)
      .map((e, i) => ({ id: newId((e.t || noon) + i), t: e.t || noon + i, ml: e.ml, f: e.f ?? 1, c: e.c || 0, n: e.n || "Water" }));
  }
  const hist = {};
  for (const [k, v] of Object.entries(H || {})) if (v && v.e >= 0 && v.g > 0) hist[k] = { e: v.e, g: v.g };
  return { v: 2, profile, days, hist };
}

/** Moves data from the old version into the new store, once. */
export async function migrateOldVersion() {
  if (localStorage.getItem("shoreline-migrated")) return false;
  let S = null, H = {};
  try { S = JSON.parse(localStorage.getItem("watr-proto-1")); } catch { /* none */ }
  try { H = JSON.parse(localStorage.getItem("watr-hist-1")) || {}; } catch { /* none */ }
  localStorage.setItem("shoreline-migrated", "1");
  if (!S && !Object.keys(H).length) return false;
  await store.importAll(fromV1(S || {}, H));
  return true;
}
