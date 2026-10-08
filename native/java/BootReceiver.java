package app.shoreline.water;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot, an app update or a clock/time-zone change: re-plan and redraw. */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c, Intent i) {
    Scheduler.schedule(c);
    WidgetBase.refresh(c, -1);
  }
}
