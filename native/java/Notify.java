package app.shoreline.water;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

/** Shoreline's notifications: drink reminders with buttons, plus quieter caffeine and weight nudges. */
public final class Notify {
  static final int REMINDER_ID = 4242;
  static final int NUDGE_ID = 4243;
  static final String CH_DRINK = "drink";
  static final String CH_NUDGE = "nudges";
  static final String KEY_ML = "ml";

  private Notify() {}

  static NotificationManager nm(Context c) { return c.getSystemService(NotificationManager.class); }

  static void channels(Context c) {
    NotificationManager nm = nm(c);
    if (nm.getNotificationChannel(CH_DRINK) == null) {
      NotificationChannel ch = new NotificationChannel(CH_DRINK, "Drink reminders", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(Uri.parse("android.resource://" + c.getPackageName() + "/raw/drop"),
          new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
      ch.enableVibration(true);
      nm.createNotificationChannel(ch);
    }
    if (nm.getNotificationChannel(CH_NUDGE) == null) {
      nm.createNotificationChannel(new NotificationChannel(CH_NUDGE, "Caffeine and weight nudges", NotificationManager.IMPORTANCE_DEFAULT));
    }
  }

  /** "Time for water": +cup, Custom (type an amount right in the notification), Snooze 30 min. */
  static void reminder(Context c, int left) {
    if (!nm(c).areNotificationsEnabled()) return;
    channels(c);
    Icon ic = Icon.createWithResource(c, R.drawable.ic_stat_shoreline);
    int cup = Store.cup(c, 1);
    RemoteInput input = new RemoteInput.Builder(KEY_ML).setLabel("ml").build();
    // A reply action needs a mutable PendingIntent so Android can attach the typed text.
    int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
    PendingIntent custom = PendingIntent.getBroadcast(c, 9101,
        new Intent(c, ActionReceiver.class).setAction(ActionReceiver.CUSTOM),
        mutable | PendingIntent.FLAG_UPDATE_CURRENT);

    Notification n = new Notification.Builder(c, CH_DRINK)
        .setSmallIcon(R.drawable.ic_stat_shoreline)
        .setContentTitle("Time for water")
        .setContentText(left + " ml left today")
        .setAutoCancel(true)
        .setContentIntent(open(c, "home"))
        .addAction(new Notification.Action.Builder(ic, "+" + cup + " ml", ActionReceiver.addIntent(c, cup)).build())
        .addAction(new Notification.Action.Builder(ic, "Custom", custom).addRemoteInput(input).build())
        .addAction(new Notification.Action.Builder(ic, "Snooze 30 min", ActionReceiver.simple(c, ActionReceiver.SNOOZE, 9102)).build())
        .build();
    nm(c).notify(REMINDER_ID, n);
  }

  static void nudge(Context c, String title, String text, String target) {
    if (!nm(c).areNotificationsEnabled()) return;
    channels(c);
    Notification n = new Notification.Builder(c, CH_NUDGE)
        .setSmallIcon(R.drawable.ic_stat_shoreline)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(open(c, target))
        .build();
    nm(c).notify(NUDGE_ID, n);
  }

  static void clearReminder(Context c) { nm(c).cancel(REMINDER_ID); }

  /** Opens the app, optionally at a screen ("home", "settings:you"). */
  static PendingIntent open(Context c, String target) {
    Intent i = new Intent(c, MainActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("open", target);
    return PendingIntent.getActivity(c, target.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
