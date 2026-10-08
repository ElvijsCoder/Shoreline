package app.shoreline.water;

import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Every button and alarm ends up here: widget and notification "+ml", undo, the typed Custom
 * amount, snooze, reminders, the midnight refresh and the nudges. Not exported, so only
 * Shoreline itself (through its own PendingIntents) can trigger it.
 */
public class ActionReceiver extends BroadcastReceiver {
  static final String ADD = "app.shoreline.water.ADD";
  static final String UNDO = "app.shoreline.water.UNDO";
  static final String CUSTOM = "app.shoreline.water.CUSTOM";
  static final String SNOOZE = "app.shoreline.water.SNOOZE";

  static PendingIntent addIntent(Context c, int ml) {
    Intent i = new Intent(c, ActionReceiver.class).setAction(ADD).putExtra("ml", ml);
    return PendingIntent.getBroadcast(c, 8000 + ml, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  static PendingIntent simple(Context c, String action, int code) {
    return PendingIntent.getBroadcast(c, code, new Intent(c, ActionReceiver.class).setAction(action),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  @Override public void onReceive(Context c, Intent i) {
    String a = i.getAction();
    if (a == null) return;
    final PendingResult pr = goAsync();
    new Thread(() -> {
      try { handle(c, a, i); } catch (Exception ignored) { } finally { pr.finish(); }
    }).start();
  }

  private void handle(Context c, String a, Intent i) throws InterruptedException {
    switch (a) {
      case ADD: add(c, i.getIntExtra("ml", 250)); break;
      case CUSTOM: {
        Bundle r = RemoteInput.getResultsFromIntent(i);
        int ml = 0;
        try { ml = Integer.parseInt(String.valueOf(r.getCharSequence(Notify.KEY_ML)).trim()); } catch (Exception ignored) { }
        if (ml > 0 && ml <= 3000) add(c, ml);
        else Notify.clearReminder(c);
        break;
      }
      case UNDO: Store.undoLast(c); Store.changed(c); break;
      case SNOOZE: Notify.clearReminder(c); Scheduler.snooze(c, 30); break;
      case Scheduler.REMIND: remind(c); break;
      case Scheduler.MIDNIGHT: Store.changed(c); Scheduler.scheduleMidnight(c); break;
      case Scheduler.CAFFEINE:
        Notify.nudge(c, "Caffeine cutoff", "Last call for caffeine today. Water from here is easier on your sleep.", "home");
        break;
      case Scheduler.WEIGHT:
        Notify.nudge(c, "Quick weight check", "Your weight keeps the water goal accurate. Tap to update it.", "settings:you");
        Scheduler.scheduleWeight(c);
        break;
      case Scheduler.RECAP:
        Notify.nudge(c, "Your week", recap(c), "history");
        Scheduler.scheduleRecap(c);
        break;
      default: break;
    }
  }

  /** Adds water and lets the widget ring fill up in a few quick steps. */
  private static void add(Context c, int ml) throws InterruptedException {
    Notify.clearReminder(c);
    int[] before = Store.state(c);
    Store.addWater(c, ml);
    int[] after = Store.state(c);
    WidgetBase.animate(c, before, after);
    Store.changed(c);
  }

  /** "Goal met 5 of 7 days · 3-day streak · about 2,150 ml a day" */
  static String recap(Context c) {
    org.json.JSONObject h = Store.hist(c);
    java.util.Calendar day = java.util.Calendar.getInstance();
    int met = 0, logged = 0, total = 0;
    for (int i = 0; i < 7; i++) {
      org.json.JSONObject d = h.optJSONObject(Store.dayOf(day.getTimeInMillis()));
      if (d != null && d.optInt("e") > 0) {
        logged++;
        total += d.optInt("e");
        if (d.optInt("g", 0) > 0 && d.optInt("e") >= d.optInt("g")) met++;
      }
      day.add(java.util.Calendar.DAY_OF_YEAR, -1);
    }
    if (logged == 0) return "Nothing logged this week. A fresh start tomorrow.";
    // Streak: consecutive goal days back from today (today only counts once it's met)
    java.util.Calendar s = java.util.Calendar.getInstance();
    int streak = 0;
    if (!met(h, s)) s.add(java.util.Calendar.DAY_OF_YEAR, -1);
    while (met(h, s)) { streak++; s.add(java.util.Calendar.DAY_OF_YEAR, -1); }
    return String.format(java.util.Locale.US, "Goal met %d of 7 days · %s · about %,d ml a day",
        met, streak > 0 ? streak + "-day streak" : "no streak right now", total / logged);
  }

  private static boolean met(org.json.JSONObject h, java.util.Calendar day) {
    org.json.JSONObject d = h.optJSONObject(Store.dayOf(day.getTimeInMillis()));
    return d != null && d.optInt("g", 0) > 0 && d.optInt("e") >= d.optInt("g");
  }

  /** Only nudge when awake, behind pace, under the goal (and any fluid limit). */
  private static void remind(Context c) {
    org.json.JSONObject p = Store.profile(c);
    double[] w = Scheduler.window(p);
    int[] st = Store.state(c);
    int limit = p.optInt("limit", 0);
    boolean under = st[0] < st[1] && (limit <= 0 || st[0] < limit);
    if (p.optBoolean("remind", true) && under && Scheduler.awake(System.currentTimeMillis(), w) && !Scheduler.ahead(c)) {
      Notify.reminder(c, st[1] - st[0]);
    }
    Scheduler.scheduleReminder(c, -1);
  }
}
