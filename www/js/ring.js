// The dot ring: 60 outer dots for water, 40 inner dots for caffeine (grey, red past the limit).
// Dots light one at a time so changes feel physical; instant with reduced motion.
import { reduceMotion } from "./state.js";

const NS = "http://www.w3.org/2000/svg";
const outer = [], inner = [];
let shown = 0, want = 0, shownCaf = 0, wantCaf = 0, shownOver = 0, wantOver = 0, timer = 0;
let innerGroup;

function dots(svg, n, r, size, list, parent) {
  for (let i = 0; i < n; i++) {
    const a = (i / n) * 2 * Math.PI - Math.PI / 2, d = document.createElementNS(NS, "circle");
    d.setAttribute("cx", 150 + r * Math.cos(a));
    d.setAttribute("cy", 150 + r * Math.sin(a));
    d.setAttribute("r", size);
    (parent || svg).appendChild(d);
    list.push(d);
  }
}

export function initRing(svg) {
  dots(svg, 60, 122, 4.2, outer);
  innerGroup = document.createElementNS(NS, "g");
  innerGroup.style.transition = "opacity .6s";
  innerGroup.style.opacity = 0;
  svg.appendChild(innerGroup);
  dots(svg, 40, 106, 2.8, inner, innerGroup);
  paint();
}

function paint() {
  outer.forEach((d, i) => d.setAttribute("fill", i < shown ? "#f3f3ef" : "#2c2c2c"));
  inner.forEach((d, i) => d.setAttribute("fill", i < shownOver ? "#ff3b30" : i < shownCaf ? "#b4b4af" : "#1e1e1e"));
}

function step() {
  let moved = false;
  if (shown !== want) { shown += shown < want ? 1 : -1; moved = true; }
  if (shownCaf !== wantCaf) { shownCaf += shownCaf < wantCaf ? 1 : -1; moved = true; }
  if (shownOver !== wantOver && (shownCaf === 40 || shownOver > wantOver)) { shownOver += shownOver < wantOver ? 1 : -1; moved = true; }
  paint();
  timer = moved ? setTimeout(step, 75) : 0;
}

/** p = water fraction; caf / over = caffeine fraction of the limit and how far past it. */
export function setRing(p, caf, over, hasCaffeine) {
  want = Math.round(60 * Math.min(p, 1));
  wantCaf = Math.round(40 * Math.min(caf, 1));
  wantOver = Math.round(40 * Math.min(over, 1));
  innerGroup.style.opacity = hasCaffeine ? 1 : 0;
  if (reduceMotion) { shown = want; shownCaf = wantCaf; shownOver = wantOver; paint(); return; }
  if (!timer) step();
}
