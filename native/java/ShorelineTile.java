package app.shoreline.water;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap to add your middle cup; shows progress underneath. */
public class ShorelineTile extends TileService {
  /** Ask the system to refresh the tile after a change made elsewhere. */
  static void ping(Context c) {
    try { requestListeningState(c, new ComponentName(c, ShorelineTile.class)); } catch (Exception ignored) { }
  }

  private void update() {
    Tile t = getQsTile();
    if (t == null) return;
    int[] st = Store.state(this);
    t.setLabel("Shoreline +" + Store.cup(this, 1));
    if (Build.VERSION.SDK_INT >= 29) {
      t.setSubtitle(Math.round(st[0] * 100f / st[1]) + "% · " + WidgetBase.left(st[0], st[1]));
    }
    t.setState(st[0] >= st[1] ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
    t.updateTile();
  }

  @Override public void onStartListening() { update(); }

  @Override public void onClick() {
    Store.addWater(this, Store.cup(this, 1));
    Store.changed(this);
    update();
  }
}
