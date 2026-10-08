package app.shoreline.water;

import android.content.Context;
import android.widget.RemoteViews;

/** 4×2: the ring, what's left, two cup buttons (your middle and large cups) and UNDO. */
public class WideWidget extends WidgetBase {
  @Override int layout() { return R.layout.widget_wide; }
  @Override void wire(Context c, RemoteViews v) {
    int a = Store.cup(c, 1), b = Store.cup(c, 2);
    v.setTextViewText(R.id.b1, "+" + a + " ml");
    v.setTextViewText(R.id.b2, "+" + b + " ml");
    v.setOnClickPendingIntent(R.id.b1, ActionReceiver.addIntent(c, a));
    v.setOnClickPendingIntent(R.id.b2, ActionReceiver.addIntent(c, b));
  }
}
