// The line of text under the ring: a warning if something needs attention, otherwise a fact.
import { dayWindow, expectedFraction, isAwake, hours, caffeineCap } from "./goal.js";

const FACTS = [
  ["About 1.4% body water loss lowered mood and brought on headaches in a study of young women.",
    "In young men, mild dehydration raised fatigue and tension and made mental tasks harder."],
  ["Water carries nutrients and helps cool you through sweat.", "Roughly half or more of an adult's body weight is water."],
  ["Pale yellow urine is a simple sign you are well hydrated.", "Food supplies about a fifth of your daily water."],
  ["Kidneys clear roughly a litre an hour at most. Steady sipping beats chugging.", "The body cannot bank extra water, so spread it across the day."],
  ["Goal reached. Extra water beyond this is mostly passed straight out.", "Done for today. From here, thirst and urine colour are your guide."],
];

export function tip({ p, caf, entries, profile, goal }) {
  const now = new Date(), h = now.getHours() + now.getMinutes() / 60;
  const win = dayWindow(profile.wake, profile.slp);
  const lastHour = entries.filter((e) => Date.now() - e.t < 3600e3).reduce((a, e) => a + e.ml, 0);
  const last = entries[entries.length - 1];
  const cutoff = hours(profile.cut, 14);
  const late = entries.some((e) => e.c > 0 && new Date(e.t).getHours() + new Date(e.t).getMinutes() / 60 >= cutoff);
  const cap = caffeineCap(profile);

  if (profile.limit > 0 && goal >= profile.limit && p >= 1) return "You've reached the fluid limit you set. Follow your doctor's advice from here.";
  if (lastHour > 1000) return "That is a lot in one hour. Kidneys clear about a litre an hour at most, so pause for a while.";
  if (p > 1.5) return "Well past your goal. No more water is needed today.";
  if (caf > cap) return "You are over today's caffeine limit. Stick to water for the rest of the day.";
  if (last && last.c > 0 && Date.now() - last.t < 6e5 && (last.ml * last.c) / 100 > Math.min(200, 3 * profile.kg))
    return "That is a big single dose of caffeine. Up to about 200 mg at once is considered fine.";
  if (late) return "Caffeine this late can cost you sleep. Even 100 mg close to bedtime can affect it.";
  if (profile.temp >= 25 && p < 1) return "Hot day: your goal is raised. Sip steadily rather than all at once.";
  if (p < 1 && isAwake(h, win) && expectedFraction(h, win) - p > 0.2) return "Behind pace for this time of day. A glass now catches you up.";
  return FACTS[p >= 1 ? 4 : p >= 0.75 ? 3 : p >= 0.5 ? 2 : p >= 0.25 ? 1 : 0][now.getDate() % 2];
}
