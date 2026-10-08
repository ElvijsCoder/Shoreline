// A mechanical counter: when a number changes, only the digits that change roll to their
// new value (up when the number grows, down when it shrinks), like Black Sand's tape counter.
import { reduceMotion } from "./state.js";

const value = (s) => +String(s).replace(/[^\d]/g, "") || 0;

export function rollTo(el, text) {
  const prev = el.dataset.text;
  if (prev === text) return;
  clearTimeout(el._roll); // a newer value wins over an unfinished roll
  el.dataset.text = text;
  if (reduceMotion || prev === undefined || !/\d/.test(text) || !/\d/.test(prev)) { el.textContent = text; return; }

  const up = value(text) > value(prev);
  // Line the two strings up from the right, so units stay over units.
  const len = Math.max(text.length, prev.length);
  const a = prev.padStart(len, " "), b = text.padStart(len, " ");
  el.textContent = "";
  for (let i = 0; i < len; i++) {
    if (a[i] === b[i]) { el.append(b[i] === " " ? "" : b[i]); continue; }
    const cell = document.createElement("span");
    cell.className = "roll";
    const col = document.createElement("span");
    col.className = up ? "up" : "down";
    const oldC = document.createElement("span"), newC = document.createElement("span");
    oldC.textContent = a[i] === " " ? "\u00a0" : a[i];
    newC.textContent = b[i] === " " ? "\u00a0" : b[i];
    up ? col.append(oldC, newC) : col.append(newC, oldC);
    cell.append(col);
    el.append(cell);
  }
  el._roll = setTimeout(() => { el.textContent = text; }, 360); // tidy up once the wheels stop
}
