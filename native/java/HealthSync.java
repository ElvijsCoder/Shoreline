package app.shoreline.water;

import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.RecordIdFilter;
import android.health.connect.datatypes.HydrationRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.units.Volume;
import android.os.Build;
import android.os.OutcomeReceiver;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sends your water to Health Connect, Android's shared health store (built into Android 14+).
 * Write-only: Shoreline never reads anything back. Each drink is written with its Shoreline id,
 * so removing a drink here removes it there, and re-sending one never creates a duplicate.
 * Every method quietly does nothing unless it's switched on and allowed.
 */
final class HealthSync {
  static final String PERMISSION = "android.permission.health.WRITE_HYDRATION";

  private HealthSync() {}

  static boolean available() { return Build.VERSION.SDK_INT >= 34; }

  static boolean granted(Context c) {
    return available() && c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
  }

  static boolean enabled(Context c) { return granted(c) && Store.profile(c).optBoolean("hc", false); }

  static void write(Context c, JSONObject entry) {
    List<JSONObject> one = new ArrayList<>();
    if (entry != null) one.add(entry);
    write(c, one);
  }

  static void write(Context c, List<JSONObject> entries) {
    if (entries.isEmpty() || !enabled(c)) return;
    try {
      List<Record> records = new ArrayList<>();
      for (JSONObject e : entries) {
        String id = e.optString("id", "");
        double ml = e.optDouble("ml", 0);
        if (id.isEmpty() || ml <= 0) continue;
        Instant start = Instant.ofEpochMilli(e.optLong("t"));
        Instant end = start.plusSeconds(60); // a drink takes a moment; Health Connect needs end after start
        ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(start);
        Metadata md = new Metadata.Builder().setClientRecordId(id).build();
        records.add(new HydrationRecord.Builder(md, start, end, Volume.fromLiters(ml / 1000.0))
            .setStartZoneOffset(offset).setEndZoneOffset(offset).build());
      }
      if (records.isEmpty()) return;
      manager(c).insertRecords(records, Runnable::run,
          new OutcomeReceiver<InsertRecordsResponse, HealthConnectException>() {
            @Override public void onResult(InsertRecordsResponse r) { }
            @Override public void onError(HealthConnectException err) { }
          });
    } catch (Throwable ignored) { } // never let syncing break logging
  }

  static void delete(Context c, List<String> ids) {
    if (ids.isEmpty() || !enabled(c)) return;
    try {
      List<RecordIdFilter> filters = new ArrayList<>();
      for (String id : ids) filters.add(RecordIdFilter.fromClientRecordId(HydrationRecord.class, id));
      manager(c).deleteRecords(filters, Runnable::run,
          new OutcomeReceiver<Void, HealthConnectException>() {
            @Override public void onResult(Void r) { }
            @Override public void onError(HealthConnectException err) { }
          });
    } catch (Throwable ignored) { }
  }

  static void delete(Context c, String id) {
    List<String> one = new ArrayList<>();
    if (id != null && !id.isEmpty()) one.add(id);
    delete(c, one);
  }

  static List<String> ids(JSONArray entries) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < entries.length(); i++) {
      JSONObject e = entries.optJSONObject(i);
      if (e != null && !e.optString("id", "").isEmpty()) out.add(e.optString("id"));
    }
    return out;
  }

  static List<JSONObject> list(JSONArray entries) {
    List<JSONObject> out = new ArrayList<>();
    for (int i = 0; i < entries.length(); i++) if (entries.optJSONObject(i) != null) out.add(entries.optJSONObject(i));
    return out;
  }

  /** When switched on: send the last [days] days, so Health Connect isn't empty. */
  static void backfill(Context c, int days) {
    List<JSONObject> all = new ArrayList<>();
    Calendar day = Calendar.getInstance();
    for (int i = 0; i < days; i++) {
      all.addAll(list(Store.day(c, Store.dayOf(day.getTimeInMillis()))));
      day.add(Calendar.DAY_OF_YEAR, -1);
    }
    write(c, all);
  }

  private static HealthConnectManager manager(Context c) {
    return c.getSystemService(HealthConnectManager.class);
  }
}
