// The drop sound and haptics. Haptics go through the native plugin (crisp predefined clicks).
import { store } from "./store.js";

let ctx, drop;
async function sound() {
  ctx = ctx || new AudioContext();
  if (ctx.state === "suspended") ctx.resume();
  if (!drop) drop = await ctx.decodeAudioData(await (await fetch("drop.wav")).arrayBuffer());
  const t = ctx.currentTime, src = ctx.createBufferSource(), lp = ctx.createBiquadFilter(), g = ctx.createGain();
  src.buffer = drop;
  lp.type = "lowpass";
  lp.frequency.value = 2800;
  g.gain.setValueAtTime(0, t);
  g.gain.linearRampToValueAtTime(0.3, t + 0.012);
  g.gain.exponentialRampToValueAtTime(0.001, t + 0.3);
  src.connect(lp).connect(g).connect(ctx.destination);
  src.start(t);
}

export const playDrop = () => sound().catch(() => {});

/** "tick" for small taps, "click" for a drink, "goal" when the goal is reached, "clear" for wiping a day. */
export function haptic(kind) {
  const patterns = { goal: [20, 60, 20, 60, 40], clear: [40, 80, 40, 80, 120], undo: [8, 40, 8] };
  if (patterns[kind]) store.haptic({ type: "pattern", pattern: patterns[kind] });
  else store.haptic({ type: kind === "click" ? "click" : "tick" });
}
