package app.shoreline.water;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The one place Shoreline's data lives. The web app, widgets, Quick Settings tile and
 * notification buttons all read and write through here, so they can never disagree.
 *
 * Every drink is a timestamped entry {id, t, ml, f, c, n} stored under its own calendar day
 * ("d_2026-10-08"), so a drink always lands on the day it was actually drunk.
 * "hist" keeps a small per-day summary {e: effective ml, g: goal} for charts and streaks.
 * "profile" is the user's settings as JSON, written by the web app.
 */
public final class Store {
  static final String PREFS = "shoreline";
  private static final Object LOCK = new Object();

  private Store() {}

  static SharedPreferences sp(Context c) {
    return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  static String dayOf(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }
  static String today() { return dayOf(System.currentTimeMillis()); }

  // --- Profile -----------------------------------------------------------------------

  static JSONObject profile(Context c) {
    try { return new JSONObject(sp(c).getString("profile", "{}")); } catch (JSONException e) { return new JSONObject(); }
  }

  static void setProfile(Context c, JSONObject p) {
    synchronized (LOCK) { sp(c).edit().putString("profile", p.toString()).commit(); }
  }

  /** Quick-add cup sizes from the profile (default 150 / 250 / 500 ml). */
  static int cup(Context c, int i) {
    int[] def = {150, 250, 500};
    JSONArray cups = profile(c).optJSONArray("cups");
    int v = cups != null ? cups.optInt(i, def[i]) : def[i];
    return v > 0 && v <= 3000 ? v : def[i];
  }

  // --- Days --------------------------------------------------------------------------

  static JSONArray day(Context c, String date) {
    try { return new JSONArray(sp(c).getString("d_" + date, "[]")); } catch (JSONException e) { return new JSONArray(); }
  }

  static JSONObject hist(Context c) {
    try { return new JSONObject(sp(c).getString("hist", "{}")); } catch (JSONException e) { return new JSONObject(); }
  }

  static int effective(JSONArray entries) {
    double sum = 0;
    for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.optJSONObject(i);
      if (e != null) sum += e.optDouble("ml", 0) * e.optDouble("f", 1);
    }
    return (int) Math.round(sum);
  }

  static double caffeine(JSONArray entries) {
    double sum = 0;
    for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.optJSONObject(i);
      if (e != null) sum += e.optDouble("ml", 0) * e.optDouble("c", 0) / 100.0;
    }
    return sum;
  }

  /** Today's goal: what the app last set for today, else the latest known goal, else 2200 ml. */
  static int goal(Context c) {
    JSONObject h = hist(c);
    JSONObject t = h.optJSONObject(today());
    if (t != null && t.optInt("g", 0) > 0) return t.optInt("g");
    int g = profile(c).optInt("goal", 0);
    return g > 0 ? g : 2200;
  }

  /** {effective ml today, goal} */
  static int[] state(Context c) {
    return new int[]{effective(day(c, today())), Math.max(1, goal(c))};
  }

  // --- Changes (all locked, all followed by changed()) -----------------------------

  /** Adds a drink. [id] may be null (one is made up); [t] decides which day it belongs to. */
  static JSONObject add(Context c, int ml, double f, double caf, String name, long t, String id) {
    JSONObject e = new JSONObject();
    synchronized (LOCK) {
      try {
        e.put("id", id != null ? id : t + "-" + (int) (Math.random() * 1e6));
        e.put("t", t).put("ml", ml).put("f", f).put("c", caf).put("n", name == null ? "Water" : name);
        String date = dayOf(t);
        JSONArray arr = day(c, date);
        arr.put(e);
        write(c, date, sortByTime(arr));
      } catch (JSONException ignored) { }
    }
    if (caf > 0) Scheduler.scheduleCaffeine(c);
    return e;
  }

  /** Adds plain water now (widget, tile, notification). */
  static JSONObject addWater(Context c, int ml) {
    return add(c, ml, 1, 0, "Water", System.currentTimeMillis(), null);
  }

  /** Removes today's most recent drink, from any source. Returns it, or null if none. */
  static JSONObject undoLast(Context c) {
    synchronized (LOCK) {
      String date = today();
      JSONArray arr = day(c, date);
      if (arr.length() == 0) return null;
      JSONObject last = arr.optJSONObject(arr.length() - 1); // entries are kept in time order
      arr.remove(arr.length() - 1);
      write(c, date, arr);
      return last;
    }
  }

  static boolean remove(Context c, String date, String id) {
    synchronized (LOCK) {
      JSONArray arr = day(c, date), out = new JSONArray();
      boolean found = false;
      for (int i = 0; i < arr.length(); i++) {
        JSONObject e = arr.optJSONObject(i);
        if (e != null && id.equals(e.optString("id"))) { found = true; continue; }
        out.put(e);
      }
      if (found) write(c, date, out);
      return found;
    }
  }

  /** Clears a day, returning what was removed so it can be restored. */
  static JSONArray clearDay(Context c, String date) {
    synchronized (LOCK) {
      JSONArray arr = day(c, date);
      write(c, date, new JSONArray());
      return arr;
    }
  }

  /** Puts entries back, keeping anything added since (matched by id). */
  static void restoreDay(Context c, String date, JSONArray entries) {
    synchronized (LOCK) { write(c, date, sortByTime(merge(day(c, date), entries))); }
  }

  static void setGoal(Context c, String date, int g) {
    synchronized (LOCK) {
      try {
        JSONObject h = hist(c);
        JSONObject d = h.optJSONObject(date);
        if (d == null) d = new JSONObject();
        d.put("g", g).put("e", effective(day(c, date)));
        h.put(date, d);
        sp(c).edit().putString("hist", h.toString()).commit();
      } catch (JSONException ignored) { }
    }
  }

  // --- Backup ------------------------------------------------------------------------

  static JSONObject exportAll(Context c) throws JSONException {
    JSONObject days = new JSONObject();
    for (Map.Entry<String, ?> kv : sp(c).getAll().entrySet()) {
      if (kv.getKey().startsWith("d_")) days.put(kv.getKey().substring(2), day(c, kv.getKey().substring(2)));
    }
    return new JSONObject().put("v", 2).put("profile", profile(c)).put("days", days).put("hist", hist(c));
  }

  /** Merges a backup in: drinks by id, history summaries, and the profile if one is given. */
  static void importAll(Context c, JSONObject data) throws JSONException {
    synchronized (LOCK) {
      JSONObject p = data.optJSONObject("profile");
      if (p != null && p.length() > 0) sp(c).edit().putString("profile", p.toString()).commit();
      JSONObject h = hist(c);
      JSONObject inHist = data.optJSONObject("hist");
      if (inHist != null) {
        JSONArray names = inHist.names();
        for (int i = 0; names != null && i < names.length(); i++) {
          String date = names.getString(i);
          JSONObject v = inHist.optJSONObject(date);
          if (v != null && v.has("e") && v.has("g")) h.put(date, new JSONObject().put("e", v.optInt("e")).put("g", v.optInt("g")));
        }
        sp(c).edit().putString("hist", h.toString()).commit();
      }
      JSONObject days = data.optJSONObject("days");
      if (days != null) {
        JSONArray names = days.names();
        for (int i = 0; names != null && i < names.length(); i++) {
          String date = names.getString(i);
          JSONArray in = days.optJSONArray(date);
          if (in != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) write(c, date, sortByTime(merge(day(c, date), clean(in))));
        }
      }
    }
  }

  /**
   * One-time pickup of drinks logged from the old widget/tile ("sl_pending" in Capacitor's
   * storage) before this version. They're added as water now; their exact time was never stored.
   */
  static void migrateLegacy(Context c) {
    SharedPreferences old = c.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
    if (sp(c).getBoolean("legacyPending", false)) return;
    int pending = 0;
    try { pending = Integer.parseInt(old.getString("sl_pending", "0").trim()); } catch (Exception ignored) { }
    if (pending > 0 && pending <= 10_000) addWater(c, pending);
    old.edit().putString("sl_pending", "0").apply();
    sp(c).edit().putBoolean("legacyPending", true).apply();
  }

  // --- Helpers -----------------------------------------------------------------------

  /** Writes a day and refreshes its history summary (keeping the day's goal). */
  private static void write(Context c, String date, JSONArray arr) {
    try {
      JSONObject h = hist(c);
      JSONObject d = h.optJSONObject(date);
      int g = d != null && d.optInt("g", 0) > 0 ? d.optInt("g") : goal(c);
      if (arr.length() == 0 && d == null) h.remove(date);
      else h.put(date, new JSONObject().put("e", effective(arr)).put("g", g));
      sp(c).edit().putString("d_" + date, arr.toString()).putString("hist", h.toString()).commit();
    } catch (JSONException ignored) { }
  }

  /** Only well-formed drink entries survive an import. */
  private static JSONArray clean(JSONArray in) {
    JSONArray out = new JSONArray();
    for (int i = 0; i < in.length(); i++) {
      JSONObject e = in.optJSONObject(i);
      if (e == null) continue;
      double ml = e.optDouble("ml", -1);
      long t = e.optLong("t", 0);
      if (ml <= 0 || ml > 5000 || t <= 0) continue;
      try {
        out.put(new JSONObject()
            .put("id", e.optString("id", t + "-" + i))
            .put("t", t).put("ml", ml)
            .put("f", Math.max(0, Math.min(2, e.optDouble("f", 1))))
            .put("c", Math.max(0, Math.min(500, e.optDouble("c", 0))))
            .put("n", e.optString("n", "Water")));
      } catch (JSONException ignored) { }
    }
    return out;
  }

  private static JSONArray merge(JSONArray a, JSONArray b) {
    List<String> ids = new ArrayList<>();
    JSONArray out = new JSONArray();
    for (JSONArray src : new JSONArray[]{a, b}) {
      for (int i = 0; i < src.length(); i++) {
        JSONObject e = src.optJSONObject(i);
        if (e == null || ids.contains(e.optString("id"))) continue;
        ids.add(e.optString("id"));
        out.put(e);
      }
    }
    return out;
  }

  private static JSONArray sortByTime(JSONArray arr) {
    List<JSONObject> list = new ArrayList<>();
    for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null) list.add(arr.optJSONObject(i));
    list.sort((x, y) -> Long.compare(x.optLong("t"), y.optLong("t")));
    JSONArray out = new JSONArray();
    for (JSONObject e : list) out.put(e);
    return out;
  }

  /** After any change: redraw widgets and tile, tell the open app, re-plan reminders. */
  static void changed(Context c) {
    WidgetBase.refresh(c, -1);
    ShorelineTile.ping(c);
    ShorePlugin.emitChanged();
    Scheduler.schedule(c);
  }
}
