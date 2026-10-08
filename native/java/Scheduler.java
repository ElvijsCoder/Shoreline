package app.shoreline.water;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Plans everything time-based: drink reminders, the midnight refresh, the caffeine cutoff
 * and the weekly weight check. Alarms are inexact (battery-friendly); a few minutes either
 * way doesn't matter for a drink reminder.
 *
 * The waking day can run past midnight (wake 08:00, sleep 00:30): hours after midnight are
 * treated as 24+ so night owls still get reminders.
 */
public final class Scheduler {
  static final String REMIND = "app.shoreline.water.REMIND";
  static final String MIDNIGHT = "app.shoreline.water.MIDNIGHT";
  static final String CAFFEINE = "app.shoreline.water.CAFFEINE";
  static final String WEIGHT = "app.shoreline.water.WEIGHT";

  private Scheduler() {}

  // --- The waking day ----------------------------------------------------------------

  static double hours(String s, double fallback) {
    try {
      String[] a = s.split(":");
      return Integer.parseInt(a[0]) + Integer.parseInt(a[1]) / 60.0;
    } catch (Exception e) { return fallback; }
  }

  /** {start, end} in hours; end may be past 24 when bedtime is after midnight. */
  static double[] window(JSONObject p) {
    double start = hours(p.optString("wake", "08:00"), 8);
    double end = hours(p.optString("slp", "22:30"), 22.5);
    if (end <= start) end += 24;
    return new double[]{start, end};
  }

  static double hourOf(long t) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(t);
    return c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60.0;
  }

  static double onTimeline(double h, double[] w) { return h < w[0] && h + 24 <= w[1] ? h + 24 : h; }

  /** Awake at time t, stopping reminders 30 minutes before bedtime. */
  static boolean awake(long t, double[] w) {
    double x = onTimeline(hourOf(t), w);
    return x >= w[0] && x < w[1] - 0.5;
  }

  static double expected(long t, double[] w) {
    double x = onTimeline(hourOf(t), w);
    return Math.min(1, Math.max(0, (x - w[0]) / (w[1] - w[0])));
  }

  /** The first wake-up time after t. */
  static long nextWake(long t, double[] w) {
    Calendar c = Calendar.getInstance();
    c.setTimeInMillis(t);
    c.set(Calendar.HOUR_OF_DAY, (int) w[0]);
    c.set(Calendar.MINUTE, (int) Math.round((w[0] % 1) * 60));
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (c.getTimeInMillis() <= t) c.add(Calendar.DAY_OF_YEAR, 1);
    return c.getTimeInMillis();
  }

  /** Comfortably ahead of where you'd expect to be by now. */
  static boolean ahead(Context c) {
    int[] st = Store.state(c);
    return st[0] / (double) st[1] > expected(System.currentTimeMillis(), window(Store.profile(c))) + 0.1;
  }

  /** ml drunk in the last hour (for the "that's a lot at once" pause). */
  static int lastHour(Context c) {
    JSONArray today = Store.day(c, Store.today());
    long since = System.currentTimeMillis() - 3_600_000L;
    int sum = 0;
    for (int i = 0; i < today.length(); i++) {
      JSONObject e = today.optJSONObject(i);
      if (e != null && e.optLong("t") >= since) sum += e.optInt("ml");
    }
    return sum;
  }

  // --- Planning ----------------------------------------------------------------------

  static void schedule(Context c) {
    scheduleReminder(c, -1);
    scheduleMidnight(c);
    scheduleWeight(c);
  }

  /** Next reminder: one interval from now (two if ahead), paused 2 h after over a litre in an hour. */
  static void scheduleReminder(Context c, long at) {
    JSONObject p = Store.profile(c);
    PendingIntent pi = pi(c, REMIND, 7001);
    AlarmManager am = c.getSystemService(AlarmManager.class);
    am.cancel(pi);
    if (!p.optBoolean("remind", true)) return;

    double[] w = window(p);
    long now = System.currentTimeMillis();
    long when = at;
    if (when < 0) {
      long iv = Math.max(30, p.optInt("iv", 90)) * 60_000L;
      when = now + iv;
      if (ahead(c)) when += iv;
      if (lastHour(c) > 1000) when = Math.max(when, now + 2 * 3_600_000L);
    }
    int[] st = Store.state(c);
    int limit = p.optInt("limit", 0);
    boolean done = st[0] >= st[1] || (limit > 0 && st[0] >= limit);
    if (done) when = nextWake(Math.max(when, now), w);
    else if (!awake(when, w)) when = nextWake(when, w);
    set(am, when, pi);
  }

  static void snooze(Context c, int minutes) { scheduleReminder(c, System.currentTimeMillis() + minutes * 60_000L); }

  /** Just after midnight: redraw widgets and tile for the new day. */
  static void scheduleMidnight(Context c) {
    Calendar m = Calendar.getInstance();
    m.add(Calendar.DAY_OF_YEAR, 1);
    m.set(Calendar.HOUR_OF_DAY, 0);
    m.set(Calendar.MINUTE, 0);
    m.set(Calendar.SECOND, 30);
    m.set(Calendar.MILLISECOND, 0);
    set(c.getSystemService(AlarmManager.class), m.getTimeInMillis(), pi(c, MIDNIGHT, 7002));
  }

  /** Caffeine cutoff nudge, only on days you've actually had caffeine and only before the cutoff. */
  static void scheduleCaffeine(Context c) {
    JSONObject p = Store.profile(c);
    if (Store.caffeine(Store.day(c, Store.today())) <= 0) return;
    double cut = hours(p.optString("cut", "14:00"), 14);
    Calendar t = Calendar.getInstance();
    t.set(Calendar.HOUR_OF_DAY, (int) cut);
    t.set(Calendar.MINUTE, (int) Math.round((cut % 1) * 60));
    t.set(Calendar.SECOND, 0);
    if (t.getTimeInMillis() <= System.currentTimeMillis()) return;
    set(c.getSystemService(AlarmManager.class), t.getTimeInMillis(), pi(c, CAFFEINE, 7003));
  }

  /** Weekly weight check: Mondays, an hour after waking. */
  static void scheduleWeight(Context c) {
    JSONObject p = Store.profile(c);
    PendingIntent pi = pi(c, WEIGHT, 7004);
    AlarmManager am = c.getSystemService(AlarmManager.class);
    if (!p.optBoolean("weightRemind", true)) { am.cancel(pi); return; }
    double h = Math.min(window(p)[0] + 1, 23);
    Calendar t = Calendar.getInstance();
    t.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
    t.set(Calendar.HOUR_OF_DAY, (int) h);
    t.set(Calendar.MINUTE, (int) Math.round((h % 1) * 60));
    t.set(Calendar.SECOND, 0);
    t.set(Calendar.MILLISECOND, 0);
    while (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.WEEK_OF_YEAR, 1);
    set(am, t.getTimeInMillis(), pi);
  }

  private static PendingIntent pi(Context c, String action, int code) {
    Intent i = new Intent(c, ActionReceiver.class).setAction(action);
    return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private static void set(AlarmManager am, long when, PendingIntent pi) {
    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
  }
}
