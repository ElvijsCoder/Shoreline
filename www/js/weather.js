// Today's temperature and humidity from Open-Meteo, using a rough (~1 km) location.
import { store } from "./store.js";
import { state, saveProfile } from "./state.js";

let busy = false;

/** Updates at most every 4 hours unless forced. `status` gets short messages for the UI. */
export async function updateWeather(force = false, status = () => {}) {
  if (busy) return;
  if (!force && (!state.profile.autoWeather || Date.now() - (state.profile.wxT || 0) < 4 * 3600e3)) return;
  busy = true;
  try {
    status("Updating weather…");
    const { lat, lon } = await store.locate();
    const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}`
      + "&daily=temperature_2m_mean,relative_humidity_2m_mean&forecast_days=1&timezone=auto";
    const r = await fetch(url);
    if (!r.ok) throw new Error(`weather service answered ${r.status}`);
    const j = await r.json();
    const temp = Math.round(j.daily.temperature_2m_mean[0]);
    const rh = Math.round(j.daily.relative_humidity_2m_mean[0]);
    saveProfile({ temp, rh, alt: Math.max(0, Math.round(j.elevation || 0)), wxT: Date.now() });
    status(`Updated ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}: ${temp} °C, ${rh}% humidity.`);
    return true;
  } catch (e) {
    const msg = String(e?.message || e);
    status(msg === "denied"
      ? "Location not allowed. Enter the weather by hand, or allow location for Shoreline in Android settings."
      : `Couldn't update the weather (${msg}). The last values are kept.`);
    return false;
  } finally {
    busy = false;
  }
}
