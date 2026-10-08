package app.shoreline.water;
import android.content.Context;
import android.widget.RemoteViews;

public class WideWidget extends WidgetBase {
  int layout() { return R.layout.widget_wide; }
  void wire(Context c, RemoteViews v) {
    v.setOnClickPendingIntent(R.id.b250, addPI(c, 250));
    v.setOnClickPendingIntent(R.id.b500, addPI(c, 500));
  }
}
