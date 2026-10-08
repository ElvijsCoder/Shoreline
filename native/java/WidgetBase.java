package app.shoreline.water;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.RemoteViews;

/**
 * Shared drawing for both widgets: a ring of 60 dots that fills as you drink, with the
 * percentage in dot-matrix digits in the middle, like the app.
 */
public abstract class WidgetBase extends AppWidgetProvider {
  /** 5×7 dot-matrix digits 0–9 and "%". */
  static final String[] GLYPHS = {
    "01110,10001,10011,10101,11001,10001,01110", "00100,01100,00100,00100,00100,00100,01110",
    "01110,10001,00001,00010,00100,01000,11111", "11110,00001,00001,01110,00001,00001,11110",
    "00010,00110,01010,10010,11111,00010,00010", "11111,10000,11110,00001,00001,10001,01110",
    "00110,01000,10000,11110,10001,10001,01110", "11111,00001,00010,00100,01000,01000,01000",
    "01110,10001,10001,01110,10001,10001,01110", "01110,10001,10001,01111,00001,00010,01100",
    "11001,11010,00010,00100,01000,01011,10011"};
  static final int RING_PX = 240; // ~230 KB per frame instead of ~520 KB

  abstract int layout();
  abstract void wire(Context c, RemoteViews v);

  /** [shown] = how many ring dots to light (-1 = from the real total), used by the fill-up animation. */
  static Bitmap ring(int eff, int goal, int shown) {
    int px = RING_PX;
    Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint on = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
    on.setColor(0xFFF3F3EF);
    off.setColor(0xFF2C2C2C);
    float cx = px / 2f, radius = px * 0.44f, dot = px * 0.019f;
    int lit = shown >= 0 ? shown : Math.round(60f * Math.min(eff / (float) goal, 1f));
    for (int i = 0; i < 60; i++) {
      double a = i / 60.0 * 2 * Math.PI - Math.PI / 2;
      c.drawCircle((float) (cx + radius * Math.cos(a)), (float) (cx + radius * Math.sin(a)), dot, i < lit ? on : off);
    }
    String t = Math.round(eff * 100f / goal) + "%";
    float cell = Math.min(px * 0.05f, px * 0.52f / (t.length() * 6 - 1));
    dotText(c, t, cx, cx, cell, on);
    return b;
  }

  /** Draws text (digits and %) in 5×7 dot-matrix, centred on (cx, cy). */
  static void dotText(Canvas c, String t, float cx, float cy, float cell, Paint paint) {
    int n = t.length(), cols = n * 6 - 1;
    float d = cell * 0.4f, x0 = cx - cols * cell / 2f, y0 = cy - 7 * cell / 2f;
    for (int k = 0; k < n; k++) {
      char ch = t.charAt(k);
      String[] g = GLYPHS[ch == '%' ? 10 : Math.max(0, Math.min(9, ch - '0'))].split(",");
      for (int row = 0; row < 7; row++)
        for (int col = 0; col < 5; col++)
          if (g[row].charAt(col) == '1') c.drawCircle(x0 + (k * 6 + col + .5f) * cell, y0 + (row + .5f) * cell, d, paint);
    }
  }

  /** The picture on the left of the widget; the ring by default (the wide widget draws the shoreline). */
  Bitmap art(int eff, int goal, int shown) { return ring(eff, goal, shown); }

  static String left(int eff, int goal) {
    return eff >= goal ? "goal reached" : String.format(java.util.Locale.US, "%,d ml left", goal - eff);
  }

  void draw(Context c, AppWidgetManager m, int id, int shown) {
    int[] st = Store.state(c);
    RemoteViews v = new RemoteViews(c.getPackageName(), layout());
    v.setImageViewBitmap(R.id.ring, art(st[0], st[1], shown));
    v.setTextViewText(R.id.left, left(st[0], st[1]));
    v.setOnClickPendingIntent(R.id.root, Notify.open(c, "home"));
    v.setOnClickPendingIntent(R.id.undo, ActionReceiver.simple(c, ActionReceiver.UNDO, 9103));
    wire(c, v);
    m.updateAppWidget(id, v);
  }

  static void refresh(Context c, int shown) {
    AppWidgetManager m = AppWidgetManager.getInstance(c);
    for (WidgetBase w : new WidgetBase[]{new SmallWidget(), new WideWidget()})
      for (int id : m.getAppWidgetIds(new ComponentName(c, w.getClass()))) w.draw(c, m, id, shown);
  }

  /** The ring fills up in at most 6 quick frames (skipped when Android's animations are off). */
  static void animate(Context c, int[] before, int[] after) throws InterruptedException {
    float scale = android.provider.Settings.Global.getFloat(c.getContentResolver(),
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    if (scale == 0f) return;
    int a = Math.round(60f * Math.min(before[0] / (float) before[1], 1f));
    int b = Math.round(60f * Math.min(after[0] / (float) after[1], 1f));
    int frames = Math.min(6, Math.abs(b - a));
    for (int f = 1; f <= frames; f++) {
      refresh(c, a + (b - a) * f / frames);
      Thread.sleep(90);
    }
  }

  @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    for (int id : ids) draw(c, m, id, -1);
  }
}
