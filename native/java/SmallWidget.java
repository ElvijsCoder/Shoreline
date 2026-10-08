package app.shoreline.water;
import android.content.Context;
import android.widget.RemoteViews;

public class SmallWidget extends WidgetBase {
  int layout() { return R.layout.widget_small; }
  void wire(Context c, RemoteViews v) { v.setOnClickPendingIntent(R.id.ring, addPI(c, 250)); }
}
