// Sounds (the drop, and the wave when you reach your goal) and haptics.
// Haptics go through the native plugin for crisp predefined clicks.
import { store } from "./store.js";

let ctx;
const buffers = {};

async function play(name, { gain = 0.3, lowpass = 2800, length = 0.3 } = {}) {
  ctx = ctx || new AudioContext();
  if (ctx.state === "suspended") ctx.resume();
  buffers[name] = buffers[name] || ctx.decodeAudioData(await (await fetch(`${name}.wav`)).arrayBuffer());
  const buf = await buffers[name];
  const t = ctx.currentTime, src = ctx.createBufferSource(), lp = ctx.createBiquadFilter(), g = ctx.createGain();
  src.buffer = buf;
  lp.type = "lowpass";
  lp.frequency.value = lowpass;
  g.gain.setValueAtTime(0, t);
  g.gain.linearRampToValueAtTime(gain, t + 0.012);
  g.gain.exponentialRampToValueAtTime(0.001, t + length);
  src.connect(lp).connect(g).connect(ctx.destination);
  src.start(t);
}

export const playDrop = () => play("drop").catch(() => {});
export const playWash = () => play("wash", { gain: 0.45, lowpass: 5000, length: 1.6 }).catch(() => {});

/** "tick" for small taps, "click" for a drink, "goal" when the goal is reached, "clear" for wiping a day. */
export function haptic(kind) {
  const patterns = { goal: [20, 60, 20, 60, 40, 120, 80], clear: [40, 80, 40, 80, 120], undo: [8, 40, 8] };
  if (patterns[kind]) store.haptic({ type: "pattern", pattern: patterns[kind] });
  else store.haptic({ type: kind === "click" ? "click" : "tick" });
}
