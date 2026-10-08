// The dot ring: 60 outer dots for water. 40 inner dots for caffeine: bright dots are caffeine
// still active in your body (they fade as it wears off, half-life ≈ 5 h), dim dots are what
// you've had today, red is anything past the daily limit.
// Dots change one at a time so it feels physical; instant with reduced motion.
import { reduceMotion } from "./state.js";

const NS = "http://www.w3.org/2000/svg";
const outer = [], inner = [];
const shown = { water: 0, total: 0, active: 0, over: 0 }, want = { water: 0, total: 0, active: 0, over: 0 };
let timer = 0, innerGroup;

function dots(parent, n, r, size, list) {
  for (let i = 0; i < n; i++) {
    const a = (i / n) * 2 * Math.PI - Math.PI / 2, d = document.createElementNS(NS, "circle");
    d.setAttribute("cx", 150 + r * Math.cos(a));
    d.setAttribute("cy", 150 + r * Math.sin(a));
    d.setAttribute("r", size);
    parent.appendChild(d);
    list.push(d);
  }
}

export function initRing(svg) {
  dots(svg, 60, 122, 4.2, outer);
  innerGroup = document.createElementNS(NS, "g");
  innerGroup.style.transition = "opacity .6s";
  innerGroup.style.opacity = 0;
  svg.appendChild(innerGroup);
  dots(innerGroup, 40, 106, 2.8, inner);
  paint();
}

function paint() {
  outer.forEach((d, i) => d.setAttribute("fill", i < shown.water ? "#f3f3ef" : "#2c2c2c"));
  inner.forEach((d, i) => d.setAttribute("fill",
    i < shown.over ? "#ff3b30" : i < shown.active ? "#e6e6e1" : i < shown.total ? "#55554f" : "#1e1e1e"));
}

function step() {
  let moved = false;
  for (const k of Object.keys(want)) {
    if (shown[k] !== want[k]) { shown[k] += shown[k] < want[k] ? 1 : -1; moved = true; }
  }
  paint();
  timer = moved ? setTimeout(step, 75) : 0;
}

/** Fractions of the goal / caffeine limit: water drunk, caffeine today, caffeine still active, caffeine past the limit. */
export function setRing({ water, total, active, over }) {
  want.water = Math.round(60 * Math.min(water, 1));
  want.total = Math.round(40 * Math.min(total, 1));
  want.active = Math.min(want.total, Math.round(40 * Math.min(active, 1)));
  want.over = Math.round(40 * Math.min(over, 1));
  innerGroup.style.opacity = total > 0 ? 1 : 0;
  if (reduceMotion) { Object.assign(shown, want); paint(); return; }
  if (!timer) step();
}
