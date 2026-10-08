package app.shoreline.water;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;

public class Scheduler {
  static final String ACT = "app.shoreline.water.REMIND";

  static double hh(String s, double d) {
    try { String[] a = s.split(":"); return Integer.parseInt(a[0]) + Integer.parseInt(a[1]) / 60.0; } catch (Exception e) { return d; }
  }
  static void setW(Calendar c, double w) {
    c.set(Calendar.HOUR_OF_DAY, (int) w); c.set(Calendar.MINUTE, (int) Math.round((w % 1) * 60));
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
  }
  static PendingIntent pi(Context c) {
    return PendingIntent.getBroadcast(c, 7001, new Intent(c, ReminderReceiver.class).setAction(ACT),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }
  static boolean ahead(Context c) {
    SharedPreferences p = WidgetBase.sp(c); int[] st = WidgetBase.state(c);
    double w0 = hh(p.getString("sl_wake", "08:00"), 8), w1 = hh(p.getString("sl_slp", "22:30"), 22.5) - .5;
    Calendar n = Calendar.getInstance(); double hn = n.get(Calendar.HOUR_OF_DAY) + n.get(Calendar.MINUTE) / 60.0;
    double exp = Math.min(1, Math.max(0, (hn - w0) / (w1 - w0)));
    return st[0] / (double) st[1] > exp + .1;
  }
  static void schedule(Context c) {
    SharedPreferences p = WidgetBase.sp(c); int[] st = WidgetBase.state(c);
    AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    double w0 = hh(p.getString("sl_wake", "08:00"), 8), w1 = hh(p.getString("sl_slp", "22:30"), 22.5) - .5;
    long iv = WidgetBase.parse(p.getString("sl_iv", "90"), 90) * 60000L;
    long t = System.currentTimeMillis() + iv;
    if (ahead(c)) t += iv;
    long pause = 0; try { pause = Long.parseLong(p.getString("sl_pause", "0")); } catch (Exception e) { }
    if (t < pause) t = pause;
    Calendar tc = Calendar.getInstance(); tc.setTimeInMillis(t);
    double h = tc.get(Calendar.HOUR_OF_DAY) + tc.get(Calendar.MINUTE) / 60.0;
    if (st[0] >= st[1] || h > w1) { tc.add(Calendar.DAY_OF_YEAR, 1); setW(tc, w0); }
    else if (h < w0) setW(tc, w0);
    PendingIntent pi = pi(c); am.cancel(pi);
    long when = tc.getTimeInMillis();
    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
  }
}
