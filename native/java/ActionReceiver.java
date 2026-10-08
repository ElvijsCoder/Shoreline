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
