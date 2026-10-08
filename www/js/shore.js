// The background: a dark beach on the left and the shoreline moving across as you drink.
// Rests between changes: full frame rate only while the tide is moving, a slow ambient
// wave otherwise, nothing while the app is hidden or motion is reduced.
import { reduceMotion } from "./state.js";

const canvas = document.getElementById("shore");
const x = canvas.getContext("2d");
let w = 0, h = 0, t = 0, tide = 0, target = 0, surge = 0, frame = 0, raf = 0;

function resize() {
  const dpr = Math.min(devicePixelRatio || 1, 2); // 2x is plenty for a soft background
  w = innerWidth; h = innerHeight;
  canvas.width = w * dpr; canvas.height = h * dpr;
  x.setTransform(dpr, 0, 0, dpr, 0, 0);
  draw();
}

const shoreAt = (y) => w * (0.1 + 0.72 * (tide + surge)) + Math.sin(y / 95 + t) * 18 + Math.sin(y / 38 - t * 1.7) * 7;

function draw() {
  x.clearRect(0, 0, w, h);
  const g = x.createLinearGradient(0, 0, w, 0);
  g.addColorStop(0, "#202224");
  g.addColorStop(1, "#070707");
  x.beginPath();
  x.moveTo(0, 0);
  for (let y = 0; y <= h; y += 8) x.lineTo(shoreAt(y), y);
  x.lineTo(0, h);
  x.closePath();
  x.fillStyle = g;
  x.fill();
  // Foam: a soft wide glow line (cheaper than a canvas blur) plus two fainter trailing waves.
  for (const [offset, alpha, width] of [[0, 0.12, 22], [0, 0.9, 9], [-16, 0.3, 4], [-34, 0.12, 3]]) {
    x.beginPath();
    for (let y = 0; y <= h; y += 8) {
      const sx = shoreAt(y) + offset + Math.sin(y / 50 + t * 1.3 + offset) * 4;
      y ? x.lineTo(sx, y) : x.moveTo(sx, y);
    }
    x.strokeStyle = `rgba(243,243,239,${alpha})`;
    x.lineWidth = width;
    x.stroke();
  }
}

function loop() {
  raf = 0;
  if (document.hidden) return;
  const moving = Math.abs(target - tide) > 0.0005 || surge > 0.0005;
  frame++;
  if (moving || frame % 3 === 0) { // ~20 fps ambient waves when settled
    t += moving ? 0.012 : 0.036;
    tide += (target - tide) * 0.04;
    surge *= 0.94;
    draw();
  }
  raf = requestAnimationFrame(loop);
}

const start = () => { if (!reduceMotion && !raf) raf = requestAnimationFrame(loop); };

export function initShore() {
  addEventListener("resize", resize);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) start(); });
  resize();
  start();
}

/** p = fraction of the goal (0..1). */
export function setTide(p) {
  target = Math.min(p, 1);
  if (reduceMotion) { tide = target; draw(); } else start();
}

/** A little surge forward when you drink. */
export function splash() { if (!reduceMotion) { surge = 0.05; start(); } }
