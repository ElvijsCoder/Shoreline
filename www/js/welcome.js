// First run: what Shoreline is, a few details for the goal, your day, then permissions,
// each explained before Android asks.
import { store } from "./store.js";
import { state, saveProfile, $, $$ } from "./state.js";
import { mountFields, syncFields } from "./fields.js";
import { updateWeather } from "./weather.js";

let step = 0;
const steps = () => $$("#welcome [data-step]");

function show() {
  steps().forEach((s, i) => (s.hidden = i !== step));
  $("#welcomeBack").hidden = step === 0;
  $("#welcomeNext").textContent = step === 0 ? "Start" : step === steps().length - 1 ? "Done" : "Next";
  $("#welcomeDots").innerHTML = steps().map((_, i) => `<i class="${i === step ? "on" : ""}"></i>`).join("");
  syncFields($("#welcome"));
  steps()[step].querySelector("h1")?.focus?.();
}

export function startWelcome(done) {
  const el = $("#welcome");
  mountFields(el);
  el.hidden = false;
  step = 0;
  show();
  $("#welcomeBack").onclick = () => { step--; show(); };
  $("#welcomeNext").onclick = () => {
    if (step < steps().length - 1) { step++; show(); return; }
    saveProfile({ onboarded: true });
    el.hidden = true;
    done();
  };
  $("#welcomeNotif").onclick = async () => {
    state.notifications = await store.requestNotifications();
    $("#welcomeNotifStatus").textContent = state.notifications
      ? "Reminders are on." : "Not allowed. You can turn them on later in Settings.";
  };
  $("#welcomeWeather").onclick = async () => {
    saveProfile({ autoWeather: true });
    const ok = await updateWeather(true, (m) => { $("#welcomeWeatherStatus").textContent = m; });
    if (!ok) saveProfile({ autoWeather: false });
  };
}
