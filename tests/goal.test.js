// Run with `npm test`. Covers the health math and the waking-day window.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  goal, caffeineCap, pregnancyExtra, adjFromChecks, dayWindow, onTimeline,
  expectedFraction, isAwake, effective, caffeine, streak, bestStreak,
} from "../www/js/goal.js";

const now = new Date("2026-06-15T12:00:00");
const base = { kg: 75, sex: "m", by: 1990, bf: 0, act: 1, temp: 15, rh: 60, alt: 0, pr: 0, adj: 0, limit: 0 };

test("goal is a sensible amount, rounded to 50 ml", () => {
  const g = goal(base, now);
  assert.ok(g >= 1500 && g <= 3500, `got ${g}`);
  assert.equal(g % 50, 0);
});

test("heavier, more active and hotter means a higher goal", () => {
  assert.ok(goal({ ...base, kg: 95 }, now) > goal(base, now));
  assert.ok(goal({ ...base, act: 3 }, now) > goal(base, now));
  assert.ok(goal({ ...base, temp: 32 }, now) > goal(base, now));
});

test("goal stays between 1000 and 5500 ml", () => {
  assert.equal(goal({ ...base, kg: 30, act: 0, temp: 15, rh: 0, by: 1930 }, now) >= 1000, true);
  assert.equal(goal({ ...base, kg: 200, act: 3, temp: 45, rh: 100, alt: 5000 }, now) <= 5500, true);
});

test("a doctor's fluid limit always caps the goal", () => {
  assert.equal(goal({ ...base, act: 3, limit: 1500 }, now), 1500);
});

test("nursing adds more than late pregnancy (EFSA)", () => {
  const f = { ...base, sex: "f" };
  assert.equal(pregnancyExtra({ ...f, pr: 1 }), 300);
  assert.equal(pregnancyExtra({ ...f, pr: 2 }), 700);
  assert.equal(pregnancyExtra({ ...base, pr: 2 }), 0); // only applies to women
  assert.ok(goal({ ...f, pr: 2 }, now) > goal({ ...f, pr: 1 }, now));
});

test("caffeine limit follows EFSA", () => {
  assert.equal(caffeineCap({ ...base, kg: 90 }), 400);
  assert.equal(caffeineCap({ ...base, kg: 50 }), 290);
  assert.equal(caffeineCap({ ...base, sex: "f", pr: 2 }), 200);
});

test("morning checks only adjust after three", () => {
  assert.equal(adjFromChecks([{ v: 3 }, { v: 3 }]), 0);
  assert.equal(adjFromChecks([{ v: 3 }, { v: 3 }, { v: 3 }]), 0.1);
  assert.equal(adjFromChecks([{ v: 1 }, { v: 1 }, { v: 1 }]), -0.05);
});

test("bedtime after midnight keeps a full waking day", () => {
  const win = dayWindow("08:00", "00:30");
  assert.deepEqual(win, { start: 8, end: 24.5 });
  assert.equal(onTimeline(0.25, win), 24.25); // 00:15 is still "today"
  assert.equal(isAwake(0.25, win), true);
  assert.equal(isAwake(3, win), false);
  assert.equal(isAwake(12, win), true);
  assert.ok(expectedFraction(23, win) > 0.9);
});

test("a normal bedtime works as before", () => {
  const win = dayWindow("07:00", "23:00");
  assert.equal(isAwake(23.5, win), false);
  assert.equal(expectedFraction(15, win), 0.5);
});

test("effective hydration and caffeine", () => {
  const log = [{ ml: 250, f: 1, c: 0 }, { ml: 200, f: 1, c: 40 }, { ml: 100, f: 1.1, c: 0 }];
  assert.equal(effective(log), 560);
  assert.equal(caffeine(log), 80);
});

test("streaks count consecutive goal days", () => {
  const prev = (k) => { const d = new Date(k + "T12:00"); d.setDate(d.getDate() - 1); return d.toISOString().slice(0, 10); };
  const hist = {
    "2026-06-11": { e: 2000, g: 2000 },
    "2026-06-12": { e: 2100, g: 2000 },
    "2026-06-13": { e: 900, g: 2000 },
    "2026-06-14": { e: 2000, g: 2000 },
    "2026-06-15": { e: 500, g: 2000 }, // today, not met yet: doesn't break the streak
  };
  assert.equal(streak(hist, "2026-06-15", prev), 1);
  assert.equal(bestStreak(hist), 2);
});
