package app.shoreline.water;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.lang.ref.WeakReference;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The web app's door to native Shoreline: the drink store, notifications, coarse location
 * for the weather, and haptics. Replaces the Preferences, LocalNotifications, Geolocation and
 * Haptics plugins with one small, purpose-built bridge.
 */
@CapacitorPlugin(
    name = "Shore",
    permissions = {
      @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS}),
      @Permission(alias = "location", strings = {Manifest.permission.ACCESS_COARSE_LOCATION})
    })
public class ShorePlugin extends Plugin {
  private static WeakReference<ShorePlugin> live = new WeakReference<>(null);
  private String pendingTarget;

  @Override public void load() {
    live = new WeakReference<>(this);
    Store.migrateLegacy(getContext());
    Notify.channels(getContext());
    Intent i = getActivity().getIntent();
    if (i != null) pendingTarget = i.getStringExtra("open");
  }

  /** A notification tapped while the app is already open. */
  @Override protected void handleOnNewIntent(Intent i) {
    super.handleOnNewIntent(i);
    String t = i.getStringExtra("open");
    if (t != null) notifyListeners("open", new JSObject().put("target", t));
  }

  /** Tells the open app that data changed elsewhere (widget, tile, notification). */
  static void emitChanged() {
    ShorePlugin p = live.get();
    if (p != null) new Handler(Looper.getMainLooper()).post(() -> p.notifyListeners("changed", new JSObject()));
  }

  private Context ctx() { return getContext(); }

  private static JSArray arr(JSONArray a) {
    try { return new JSArray(a.toString()); } catch (Exception e) { return new JSArray(); }
  }

  private static JSObject obj(JSONObject o) {
    try { return JSObject.fromJSONObject(o); } catch (Exception e) { return new JSObject(); }
  }

  // --- Data ----------------------------------------------------------------------------

  @PluginMethod public void getState(PluginCall call) {
    Context c = ctx();
    String today = Store.today();
    JSObject r = new JSObject();
    r.put("today", today);
    r.put("entries", arr(Store.day(c, today)));
    r.put("hist", obj(Store.hist(c)));
    r.put("profile", obj(Store.profile(c)));
    r.put("notifications", Notify.nm(c).areNotificationsEnabled());
    call.resolve(r);
  }

  @PluginMethod public void day(PluginCall call) {
    call.resolve(new JSObject().put("entries", arr(Store.day(ctx(), call.getString("date", Store.today())))));
  }

  @PluginMethod public void add(PluginCall call) {
    Double ml = call.getDouble("ml");
    if (ml == null || ml <= 0 || ml > 3000) { call.reject("Amount must be 1–3000 ml"); return; }
    Double t = call.getDouble("t");
    JSONObject e = Store.add(ctx(), (int) Math.round(ml), call.getDouble("f", 1.0), call.getDouble("c", 0.0),
        call.getString("n", "Water"), t != null ? t.longValue() : System.currentTimeMillis(), call.getString("id"));
    Store.changed(ctx());
    call.resolve(obj(e));
  }

  @PluginMethod public void remove(PluginCall call) {
    boolean ok = Store.remove(ctx(), call.getString("date", Store.today()), call.getString("id", ""));
    Store.changed(ctx());
    call.resolve(new JSObject().put("removed", ok));
  }

  @PluginMethod public void undo(PluginCall call) {
    JSONObject e = Store.undoLast(ctx());
    Store.changed(ctx());
    call.resolve(new JSObject().put("entry", e == null ? null : obj(e)));
  }

  @PluginMethod public void clearDay(PluginCall call) {
    JSONArray removed = Store.clearDay(ctx(), call.getString("date", Store.today()));
    Store.changed(ctx());
    call.resolve(new JSObject().put("entries", arr(removed)));
  }

  @PluginMethod public void restoreDay(PluginCall call) {
    JSArray entries = call.getArray("entries", new JSArray());
    Store.restoreDay(ctx(), call.getString("date", Store.today()), entries);
    Store.changed(ctx());
    call.resolve();
  }

  @PluginMethod public void setGoal(PluginCall call) {
    Store.setGoal(ctx(), call.getString("date", Store.today()), call.getInt("goal", 2200));
    WidgetBase.refresh(ctx(), -1);
    ShorelineTile.ping(ctx());
    call.resolve();
  }

  @PluginMethod public void setProfile(PluginCall call) {
    JSObject p = call.getObject("profile");
    if (p == null) { call.reject("No profile"); return; }
    Store.setProfile(ctx(), p);
    Store.changed(ctx());
    call.resolve();
  }

  @PluginMethod public void exportAll(PluginCall call) {
    try { call.resolve(obj(Store.exportAll(ctx()))); } catch (Exception e) { call.reject(e.getMessage()); }
  }

  @PluginMethod public void importAll(PluginCall call) {
    try {
      Store.importAll(ctx(), call.getObject("data", new JSObject()));
      Store.changed(ctx());
      call.resolve();
    } catch (Exception e) { call.reject("Not a Shoreline backup"); }
  }

  @PluginMethod public void launchTarget(PluginCall call) {
    call.resolve(new JSObject().put("target", pendingTarget));
    pendingTarget = null;
  }

  // --- Notifications -----------------------------------------------------------------

  @PluginMethod public void requestNotifications(PluginCall call) {
    if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
      requestPermissionForAlias("notifications", call, "notificationsResult");
    } else {
      notificationsResult(call);
    }
  }

  @PermissionCallback private void notificationsResult(PluginCall call) {
    boolean on = Notify.nm(ctx()).areNotificationsEnabled();
    if (on) Scheduler.schedule(ctx());
    call.resolve(new JSObject().put("granted", on));
  }

  // --- Location (coarse, rounded: weather doesn't need more) --------------------------

  @PluginMethod public void locate(PluginCall call) {
    if (getPermissionState("location") != PermissionState.GRANTED) {
      requestPermissionForAlias("location", call, "locationResult");
    } else {
      findLocation(call);
    }
  }

  @PermissionCallback private void locationResult(PluginCall call) {
    if (getPermissionState("location") == PermissionState.GRANTED) findLocation(call);
    else call.reject("denied");
  }

  @SuppressWarnings("MissingPermission")
  private void findLocation(PluginCall call) {
    LocationManager lm = ctx().getSystemService(LocationManager.class);
    String provider = LocationManager.NETWORK_PROVIDER;
    if (!lm.isProviderEnabled(provider)) {
      Location last = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
      if (last != null) resolveLocation(call, last); else call.reject("Location is off");
      return;
    }
    if (Build.VERSION.SDK_INT >= 30) {
      lm.getCurrentLocation(provider, null, ctx().getMainExecutor(), loc -> {
        if (loc != null) resolveLocation(call, loc);
        else {
          Location last = lm.getLastKnownLocation(provider);
          if (last != null) resolveLocation(call, last); else call.reject("No location yet");
        }
      });
    } else {
      Location last = lm.getLastKnownLocation(provider);
      if (last != null) resolveLocation(call, last); else call.reject("No location yet");
    }
  }

  /** Two decimals ≈ 1 km: plenty for weather, and less precise data leaves the phone. */
  private static void resolveLocation(PluginCall call, Location l) {
    call.resolve(new JSObject()
        .put("lat", Math.round(l.getLatitude() * 100) / 100.0)
        .put("lon", Math.round(l.getLongitude() * 100) / 100.0));
  }

  // --- Haptics -----------------------------------------------------------------------

  /** type: "tick" | "click" | "heavy" | "pattern" (with "pattern": [on, off, on, ...] in ms). */
  @PluginMethod public void haptic(PluginCall call) {
    Vibrator v = ctx().getSystemService(Vibrator.class);
    if (v == null || !v.hasVibrator()) { call.resolve(); return; }
    String type = call.getString("type", "tick");
    if (Build.VERSION.SDK_INT < 29 && !type.equals("pattern")) { // no predefined effects before Android 10
      v.vibrate(VibrationEffect.createOneShot(type.equals("heavy") ? 40 : 15, VibrationEffect.DEFAULT_AMPLITUDE));
      call.resolve();
      return;
    }
    try {
      switch (type) {
        case "click": v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)); break;
        case "heavy": v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)); break;
        case "pattern": {
          JSArray p = call.getArray("pattern", new JSArray());
          long[] timings = new long[p.length() + 1];
          for (int i = 0; i < p.length(); i++) timings[i + 1] = p.getLong(i);
          v.vibrate(VibrationEffect.createWaveform(timings, -1));
          break;
        }
        default: v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
      }
    } catch (Exception ignored) { }
    call.resolve();
  }
}
