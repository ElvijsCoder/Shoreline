package app.shoreline.water;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    int[] st = WidgetBase.state(c);
    if (st[0] < st[1] && !Scheduler.ahead(c)) post(c, st);
    Scheduler.schedule(c);
  }

  static void post(Context c, int[] st) {
    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("drink") == null) {
      NotificationChannel ch = new NotificationChannel("drink", "Drink reminders", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(Uri.parse("android.resource://" + c.getPackageName() + "/raw/drop"),
          new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
      ch.enableVibration(true);
      nm.createNotificationChannel(ch);
    }
    Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "drink") : new Notification.Builder(c);
    Icon ic = Icon.createWithResource(c, R.drawable.ic_stat_shoreline);
    b.setSmallIcon(R.drawable.ic_stat_shoreline).setContentTitle("Time for water")
        .setContentText((st[1] - st[0]) + " ml left today").setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(c, 1, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(new Notification.Action.Builder(ic, "+250 ml", new SmallWidget().addPI(c, 250)).build())
        .addAction(new Notification.Action.Builder(ic, "+500 ml", new SmallWidget().addPI(c, 500)).build());
    nm.notify(4242, b.build());
  }
}
