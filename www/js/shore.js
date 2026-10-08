// The background: a dark beach on the left and the shoreline moving across as you drink.
// - A wave washes over the screen when you reach your goal, leaving foam behind.
// - Tilt the phone and the water sloshes towards the low side.
// Full frame rate only while something moves; slow ambient waves otherwise; nothing while
// hidden; all of it off under reduced motion.
import { reduceMotion, state } from "./state.js";

const canvas = document.getElementById("shore");
const x = canvas.getContext("2d");
// On launch the shoreline starts off-screen to the left and sweeps in to today's level,
// picking up where the launch screen's icon leaves off.
let w = 0, h = 0, t = 0, tide = reduceMotion ? 0 : -0.3, target = 0, surge = 0, frame = 0, raf = 0;
const sweepFrom = performance.now() + 450; // a beat for the launch screen to fade
let tilt = 0, tiltTarget = 0; // -1 (tilted left) … 1 (tilted right)
let wave = -1; // goal wave progress 0..1, -1 when idle
const foam = []; // particles left behind by the wave

function resize() {
  const dpr = Math.min(devicePixelRatio || 1, 2);
  w = innerWidth; h = innerHeight;
  canvas.width = w * dpr; canvas.height = h * dpr;
  x.setTransform(dpr, 0, 0, dpr, 0, 0);
  draw();
}

// Tilt pushes the water further at the bottom of the screen, like it's pooling.
const shoreAt = (y) => w * (0.1 + 0.72 * (tide + surge)) + Math.sin(y / 95 + t) * 18 + Math.sin(y / 38 - t * 1.7) * 7
  - tilt * 34 * (0.35 + 0.65 * (y / h));

function strokeLine(offset, alpha, width, xAt) {
  x.beginPath();
  for (let y = 0; y <= h; y += 8) {
    const sx = xAt(y) + offset + Math.sin(y / 50 + t * 1.3 + offset) * 4;
    y ? x.lineTo(sx, y) : x.moveTo(sx, y);
  }
  x.strokeStyle = `rgba(243,243,239,${alpha})`;
  x.lineWidth = width;
  x.stroke();
}

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
  for (const [o, a, lw] of [[0, 0.12, 22], [0, 0.9, 9], [-16, 0.3, 4], [-34, 0.12, 3]]) strokeLine(o, a, lw, shoreAt);

  if (wave >= 0) {
    // The goal wave: a bright band rolling right across the whole screen.
    const ease = 1 - Math.pow(1 - wave, 3);
    const front = -80 + (w + 240) * ease;
    const waveAt = (y) => front + Math.sin(y / 70 + t * 2) * 14 + Math.sin(y / 23) * 5;
    const wash = x.createLinearGradient(front - 220, 0, front, 0);
    wash.addColorStop(0, "rgba(243,243,239,0)");
    wash.addColorStop(1, `rgba(243,243,239,${0.16 * (1 - wave)})`);
    x.fillStyle = wash;
    x.fillRect(front - 220, 0, 230, h);
    for (const [o, a, lw] of [[0, 0.15, 30], [0, 0.95 * (1 - wave * 0.6), 10], [-20, 0.35, 4]]) strokeLine(o, a, lw, waveAt);
    for (let i = 0; i < 6; i++) {
      foam.push({ x: front + Math.random() * 10, y: Math.random() * h, vx: 0.5 + Math.random() * 2.5, vy: (Math.random() - 0.5) * 0.8, r: 1 + Math.random() * 2.2, life: 1 });
    }
  }
  for (const f of foam) {
    x.beginPath();
    x.arc(f.x, f.y, f.r, 0, Math.PI * 2);
    x.fillStyle = `rgba(243,243,239,${0.75 * f.life})`;
    x.fill();
  }
}

function step() {
  for (const f of foam) { f.x += f.vx; f.y += f.vy; f.vx *= 0.98; f.life -= 0.012; }
  for (let i = foam.length - 1; i >= 0; i--) if (foam[i].life <= 0) foam.splice(i, 1);
  if (wave >= 0) { wave += 1 / 95; if (wave > 1) wave = -1; }
}

function loop() {
  raf = 0;
  if (document.hidden) return;
  tilt += (tiltTarget - tilt) * 0.06;
  const moving = Math.abs(target - tide) > 0.0005 || surge > 0.0005 || Math.abs(tiltTarget - tilt) > 0.002
    || wave >= 0 || foam.length > 0;
  frame++;
  if (moving || frame % 3 === 0) { // ~20 fps ambient waves when settled
    t += moving ? 0.012 : 0.036;
    if (performance.now() > sweepFrom) tide += (target - tide) * 0.04;
    surge *= 0.94;
    step();
    draw();
  }
  raf = requestAnimationFrame(loop);
}

const start = () => { if (!reduceMotion && !raf) raf = requestAnimationFrame(loop); };

export function initShore() {
  addEventListener("resize", resize);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) start(); });
  if (!reduceMotion) {
    addEventListener("deviceorientation", (e) => {
      if (e.gamma == null || state.profile.tilt === false) { tiltTarget = 0; return; }
      tiltTarget = Math.max(-1, Math.min(1, e.gamma / 30)); // ±30° = full slosh
      start();
    });
  }
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

/** The goal wave: rolls across the screen and leaves foam. */
export function celebrate() { if (!reduceMotion) { wave = 0; surge = 0.08; start(); } }
