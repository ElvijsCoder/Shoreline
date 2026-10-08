package app.shoreline.water;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public abstract class WidgetBase extends AppWidgetProvider {
  static final String ACT = "app.shoreline.water.ADD";
  static final String[] G = {
    "01110,10001,10011,10101,11001,10001,01110", "00100,01100,00100,00100,00100,00100,01110",
    "01110,10001,00001,00010,00100,01000,11111", "11110,00001,00001,01110,00001,00001,11110",
    "00010,00110,01010,10010,11111,00010,00010", "11111,10000,11110,00001,00001,10001,01110",
    "00110,01000,10000,11110,10001,10001,01110", "11111,00001,00010,00100,01000,01000,01000",
    "01110,10001,10001,01110,10001,10001,01110", "01110,10001,10001,01111,00001,00010,01100",
    "11001,11010,00010,00100,01000,01011,10011"};

  abstract int layout();
  abstract void wire(Context c, RemoteViews v);

  static SharedPreferences sp(Context c) { return c.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE); }
  static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
  static int parse(String s, int d) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; } }

  static int[] state(Context c) {
    SharedPreferences p = sp(c);
    int goal = Math.max(1, parse(p.getString("sl_goal", "2200"), 2200));
    int eff = today().equals(p.getString("sl_day", "")) ? parse(p.getString("sl_eff", "0"), 0) : 0;
    return new int[]{eff, goal};
  }

  static Bitmap ring(int px, int eff, int goal, int shown) {
    Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
    on.setColor(0xFFF3F3EF); off.setColor(0xFF2C2C2C);
    float cx = px / 2f, R = px * 0.44f, r = px * 0.017f;
    int lit = shown >= 0 ? shown : Math.round(60f * Math.min(eff / (float) goal, 1f));
    for (int i = 0; i < 60; i++) {
      double a = i / 60.0 * 2 * Math.PI - Math.PI / 2;
      c.drawCircle((float) (cx + R * Math.cos(a)), (float) (cx + R * Math.sin(a)), r, i < lit ? on : off);
    }
    String t = Math.round(eff * 100f / goal) + "%";
    int n = t.length(), cols = n * 6 - 1;
    float cell = Math.min(px * 0.05f, px * 0.52f / cols), d = cell * 0.4f;
    float x0 = cx - cols * cell / 2f, y0 = cx - 7 * cell / 2f;
    for (int k = 0; k < n; k++) {
      char ch = t.charAt(k);
      String[] g = G[ch == '%' ? 10 : Math.max(0, Math.min(9, ch - '0'))].split(",");
      for (int row = 0; row < 7; row++)
        for (int col = 0; col < 5; col++)
          if (g[row].charAt(col) == '1') c.drawCircle(x0 + (k * 6 + col + .5f) * cell, y0 + (row + .5f) * cell, d, on);
    }
    return b;
  }

  PendingIntent addPI(Context c, int ml) {
    Intent i = new Intent(c, getClass()).setAction(ACT).putExtra("ml", ml);
    return PendingIntent.getBroadcast(c, ml * 100 + getClass().getSimpleName().length(), i,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  void draw(Context c, AppWidgetManager m, int id, int shown) {
    int[] st = state(c);
    RemoteViews v = new RemoteViews(c.getPackageName(), layout());
    v.setImageViewBitmap(R.id.ring, ring(360, st[0], st[1], shown));
    v.setTextViewText(R.id.left, st[0] >= st[1] ? "goal reached" : (st[1] - st[0]) + " ml left");
    Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, launch,
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    wire(c, v);
    m.updateAppWidget(id, v);
  }

  static void refresh(Context c, int shown) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    for (WidgetBase w : new WidgetBase[]{new SmallWidget(), new WideWidget()})
      for (int id : m.getAppWidgetIds(new ComponentName(c, w.getClass()))) w.draw(c, m, id, shown);
  }

  static void commit(Context c, int ml) {
    SharedPreferences p = sp(c); int[] st = state(c);
    int pend = parse(p.getString("sl_pending", "0"), 0) + ml;
    p.edit().putString("sl_day", today()).putString("sl_eff", "" + (st[0] + ml)).putString("sl_pending", "" + pend).commit();
    refresh(c, -1); Scheduler.schedule(c);
  }

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    for (int id : ids) draw(c, m, id, -1);
  }

  @Override public void onReceive(final Context c, Intent i) {
    if (ACT.equals(i.getAction())) {
      final int ml = i.getIntExtra("ml", 250);
      final PendingResult pr = goAsync();
      ((android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(4242);
      new Thread(() -> {
        try {
          SharedPreferences p = sp(c);
          int[] st = state(c);
          int old = st[0], nw = old + ml;
          int pend = parse(p.getString("sl_pending", "0"), 0) + ml;
          p.edit().putString("sl_day", today()).putString("sl_eff", "" + nw).putString("sl_pending", "" + pend).commit();
          int a = Math.round(60f * Math.min(old / (float) st[1], 1f)), b = Math.round(60f * Math.min(nw / (float) st[1], 1f));
          for (int s = a; s <= b; s++) { refresh(c, s); Thread.sleep(70); }
          refresh(c, -1);
          Scheduler.schedule(c);
        } catch (Exception e) { } finally { pr.finish(); }
      }).start();
    } else super.onReceive(c, i);
  }
}
