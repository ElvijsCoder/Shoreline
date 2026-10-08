package app.shoreline.water;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.RemoteViews;

/**
 * 4×2: the app's shoreline on the left (the white foam line moves across as you drink),
 * with what's left, two cup buttons (your middle and large cups) and UNDO on the right.
 */
public class WideWidget extends WidgetBase {
  @Override int layout() { return R.layout.widget_wide; }

  @Override void wire(Context c, RemoteViews v) {
    int a = Store.cup(c, 1), b = Store.cup(c, 2);
    v.setTextViewText(R.id.b1, "+" + a + " ml");
    v.setTextViewText(R.id.b2, "+" + b + " ml");
    v.setOnClickPendingIntent(R.id.b1, ActionReceiver.addIntent(c, a));
    v.setOnClickPendingIntent(R.id.b2, ActionReceiver.addIntent(c, b));
  }

  /** [shown] 0–60 during the fill-up animation (as on the ring), -1 for the real total. */
  @Override Bitmap art(int eff, int goal, int shown) {
    float p = shown >= 0 ? shown / 60f : Math.min(eff / (float) goal, 1f);
    return shore(p, Math.round(eff * 100f / goal) + "%");
  }

  static Bitmap shore(float p, String pct) {
    int W = 336, H = 240;
    Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, W, H), 22, 22, Path.Direction.CW);
    c.clipPath(clip);
    c.drawColor(0xFF000000);

    // The beach, up to the shoreline (same shape and colours as the app)
    Path sand = new Path();
    sand.moveTo(0, 0);
    for (int y = 0; y <= H; y += 6) sand.lineTo(shoreX(y, W, H, p, 0), y);
    sand.lineTo(0, H);
    sand.close();
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setShader(new LinearGradient(0, 0, W, 0, 0xFF202224, 0xFF070707, Shader.TileMode.CLAMP));
    c.drawPath(sand, fill);

    // Foam: a soft glow, the bright line and two fainter trailing waves
    float[][] lines = {{0, 0.12f, 16}, {0, 0.9f, 6}, {-11, 0.3f, 3}, {-22, 0.12f, 2}};
    for (float[] l : lines) {
      Path foam = new Path();
      for (int y = 0; y <= H; y += 6) {
        float x = shoreX(y, W, H, p, l[0]);
        if (y == 0) foam.moveTo(x, y); else foam.lineTo(x, y);
      }
      Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
      s.setStyle(Paint.Style.STROKE);
      s.setStrokeCap(Paint.Cap.ROUND);
      s.setStrokeWidth(l[2]);
      s.setColor(0xF3F3EF | ((int) (l[1] * 255) << 24));
      c.drawPath(foam, s);
    }

    // The percentage in dot-matrix, bottom right
    Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    on.setColor(0xFFF3F3EF);
    float cell = 7.5f;
    dotText(c, pct, W - (pct.length() * 6 - 1) * cell / 2f - 16, H - 7 * cell / 2f - 16, cell, on);
    return b;
  }

  private static float shoreX(int y, int W, int H, float p, float offset) {
    return W * (0.1f + 0.72f * p) + (float) (Math.sin(y / 40.0) * 8 + Math.sin(y / 16.0) * 3) + offset;
  }
}
