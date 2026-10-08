package app.shoreline.water;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class ShorelineTile extends TileService {
  void upd() {
    Tile t = getQsTile(); if (t == null) return;
    int[] st = WidgetBase.state(this);
    t.setLabel("Shoreline +250");
    if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(Math.round(st[0] * 100f / st[1]) + "%, " + Math.max(0, st[1] - st[0]) + " ml left");
    t.setState(Tile.STATE_ACTIVE);
    t.updateTile();
  }
  @Override public void onStartListening() { upd(); }
  @Override public void onClick() { WidgetBase.commit(this, 250); upd(); }
}
