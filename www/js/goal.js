// Shoreline's numbers: daily water goal, caffeine limit and the waking-day window.
// Pure functions only (no DOM, no storage) so they can be unit-tested with `npm test`.

const PAL = [1.4, 1.6, 1.8, 2.2]; // physical activity level per activity setting

/**
 * Daily water turnover in ml (Yamada et al., Science 2022).
 * Uses the fat-free-mass equation when body fat is known, otherwise the body-mass one.
 * NOTE: the published equations also have a human development index (HDI) term. It's left
 * out until its coding is checked against the paper; see README "Science notes".
 */
export function turnover(p, now = new Date()) {
  const age = now.getFullYear() - p.by;
  const pal = PAL[p.act] ?? PAL[1];
  const athlete = p.act === 3 ? 1 : 0;
  const t = p.temp;
  if (p.bf > 0) {
    const ffm = p.kg * (1 - p.bf / 100);
    return 861.9 * pal + 37.34 * ffm + 4.288 * p.rh + 699.7 * athlete + 0.514 * p.alt
      - 0.3625 * age * age + 29.42 * age + 1.937 * t * t - 23.15 * t - 984.8;
  }
  return 1076 * pal + 14.34 * p.kg + 374.9 * (p.sex === "m" ? 1 : 0) + 5.823 * p.rh + 1070 * athlete
    + 0.4726 * p.alt - 0.3529 * age * age + 24.78 * age + 1.865 * t * t - 19.66 * t - 713.1;
}

/** Extra total water a day (EFSA 2010): about +300 ml in pregnancy, +700 ml while nursing. */
export function pregnancyExtra(p) {
  if (p.sex !== "f") return 0;
  return [0, 300, 700][p.pr] ?? 0;
}

/**
 * Today's drinking goal in ml.
 * About 85% of turnover is water taken in (the rest the body makes itself), pregnancy/nursing
 * extras are added to that, and about 80% of it comes from drinks (the rest from food).
 * Morning urine checks nudge it by up to ±10%. A doctor's fluid limit always wins.
 */
export function goal(p, now = new Date()) {
  const intake = turnover(p, now) * 0.85 + pregnancyExtra(p);
  const drinks = intake * 0.8 * (1 + (p.adj || 0));
  const g = Math.min(5500, Math.max(1000, Math.round(drinks / 50) * 50));
  return p.limit > 0 ? Math.min(g, p.limit) : g;
}

/** Caffeine limit in mg a day (EFSA 2015): 5.7 mg/kg up to 400 mg, 200 mg pregnant or nursing. */
export function caffeineCap(p) {
  if (p.sex === "f" && p.pr > 0) return 200;
  return Math.min(400, Math.round((5.7 * p.kg) / 10) * 10);
}

/** Goal adjustment from the last morning checks (1 pale, 2 yellow, 3 dark). Needs 3 checks. */
export function adjFromChecks(checks) {
  if (!checks || checks.length < 3) return 0;
  const m = checks.reduce((a, x) => a + x.v, 0) / checks.length;
  return m >= 2.5 ? 0.1 : m >= 2.1 ? 0.05 : m <= 1.2 ? -0.05 : 0;
}

/** "08:30" → 8.5 */
export function hours(s, fallback = 0) {
  const [h, m] = String(s).split(":").map(Number);
  return Number.isFinite(h) ? h + (Number.isFinite(m) ? m : 0) / 60 : fallback;
}

/**
 * The waking day as hours, where the end may run past midnight:
 * wake 08:00, sleep 00:30 → { start: 8, end: 24.5 }.
 */
export function dayWindow(wake, sleep) {
  const start = hours(wake, 8);
  let end = hours(sleep, 22.5);
  if (end <= start) end += 24;
  return { start, end };
}

/** Where `h` (0–24) falls on the waking-day timeline: after-midnight hours become 24+. */
export function onTimeline(h, win) {
  return h < win.start && h + 24 <= win.end ? h + 24 : h;
}

/** How much of the day's goal you'd expect to have drunk by hour `h` (0..1). */
export function expectedFraction(h, win) {
  const x = onTimeline(h, win);
  return Math.min(1, Math.max(0, (x - win.start) / (win.end - win.start)));
}

/** True while awake (between wake and sleep, handling sleep after midnight). */
export function isAwake(h, win) {
  const x = onTimeline(h, win);
  return x >= win.start && x < win.end;
}

/** Effective hydration of a list of drinks: ml × hydration factor. */
export function effective(entries) {
  return Math.round(entries.reduce((a, e) => a + e.ml * (e.f ?? 1), 0));
}

/** Caffeine in mg from a list of drinks (c = mg per 100 ml). */
export function caffeine(entries) {
  return entries.reduce((a, e) => a + (e.ml * (e.c || 0)) / 100, 0);
}

/** Consecutive days with the goal met, counting back from today (today only counts once met). */
export function streak(hist, todayKey, dayKeyBefore) {
  let n = 0;
  let key = todayKey;
  const met = (k) => hist[k] && hist[k].g > 0 && hist[k].e >= hist[k].g;
  if (!met(key)) key = dayKeyBefore(key);
  while (met(key)) { n++; key = dayKeyBefore(key); }
  return n;
}

/** Longest run of goal-met days in the history. */
export function bestStreak(hist) {
  const days = Object.keys(hist).sort();
  let best = 0, run = 0, prev = null;
  for (const d of days) {
    const ok = hist[d].g > 0 && hist[d].e >= hist[d].g;
    const consecutive = prev && (Date.parse(d) - Date.parse(prev)) === 86_400_000;
    run = ok ? (consecutive && run > 0 ? run + 1 : 1) : 0;
    best = Math.max(best, run);
    prev = d;
  }
  return best;
}
