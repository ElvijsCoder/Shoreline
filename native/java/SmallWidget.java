package app.shoreline.water;

import android.content.Context;
import android.widget.RemoteViews;

/** 2×2: tap the ring to add your middle cup; UNDO underneath. */
public class SmallWidget extends WidgetBase {
  @Override int layout() { return R.layout.widget_small; }
  @Override void wire(Context c, RemoteViews v) {
    int cup = Store.cup(c, 1);
    v.setOnClickPendingIntent(R.id.ring, ActionReceiver.addIntent(c, cup));
    v.setContentDescription(R.id.ring, "Add " + cup + " ml of water");
  }
}
