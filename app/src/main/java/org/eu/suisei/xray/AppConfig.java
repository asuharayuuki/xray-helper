package org.eu.suisei.xray;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {

    static final String PREFS_NAME = "xray_config";
    public static final String KEY_START_CMD = "cmd_start";
    public static final String KEY_STOP_CMD = "cmd_stop";
    public static final String KEY_HTTP_PROXY = "http_proxy";
    public static final String KEY_PROCESS_NAME = "process_name";
    public static final String KEY_RECORD_TILE_LABEL = "record_tile_label";
    public static final String KEY_RECORD_TILE_ICON = "record_tile_icon";
    public static final String KEY_RECORD_TILE_CMD = "record_tile_cmd";

    public static final String DEFAULT_START_CMD =
            "/system/bin/setsid -d /data/local/tmp/xray/xray run -c /data/local/tmp/xray/config.json </dev/null >/dev/null 2>&1 &";

    public static final String DEFAULT_STOP_CMD =
            "p=$(pidof xray)\n" +
            "if [ -n \"$p\" ]; then\n" +
            "    kill $p 2>/dev/null\n" +
            "    i=0\n" +
            "    while [ $i -lt 10 ]; do\n" +
            "        [ -z \"$(pidof xray)\" ] && break\n" +
            "        usleep 150000 2>/dev/null || sleep 1\n" +
            "        i=$((i + 1))\n" +
            "    done\n" +
            "    p=$(pidof xray)\n" +
            "    if [ -n \"$p\" ]; then\n" +
            "        kill -9 $p 2>/dev/null\n" +
            "        i=0\n" +
            "        while [ $i -lt 5 ]; do\n" +
            "            [ -z \"$(pidof xray)\" ] && break\n" +
            "            usleep 100000 2>/dev/null || sleep 1\n" +
            "            i=$((i + 1))\n" +
            "        done\n" +
            "    fi\n" +
            "fi\n" +
            "[ -z \"$(pidof xray)\" ]";

    public static final String DEFAULT_HTTP_PROXY = "127.0.0.1:2080";
    public static final String DEFAULT_PROCESS_NAME = "xray";

    public static final String DEFAULT_RECORD_TILE_ICON = "radio_button_checked";
    public static final String DEFAULT_RECORD_TILE_CMD =
            "am start -a voicenote.intent.action.START_RECORDING -n com.sec.android.app.voicenote/.activity.ShortcutActivity; cmd statusbar collapse";

    private AppConfig() {}

    public static ConfigCodec.Snapshot snapshot(Context context) throws Exception {
        return new ConfigCodec.Snapshot(getStartCommand(context), getStopCommand(context),
                getHttpProxy(context), getProcessName(context),
                DashboardConfigManager.toJson(DashboardConfigManager.load(context)).toString());
    }

    public static void importSnapshot(Context context, ConfigCodec.Snapshot snapshot) throws Exception {
        // Validate everything before making one atomic SharedPreferences update.
        ConfigCodec.Snapshot checked = ConfigCodec.decode(ConfigCodec.encode(snapshot));
        boolean saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_START_CMD, checked.startCommand)
                .putString(KEY_STOP_CMD, checked.stopCommand)
                .putString(KEY_HTTP_PROXY, checked.httpProxy)
                .putString(KEY_PROCESS_NAME, checked.processName)
                .putString(DashboardConfigManager.KEY_DASHBOARD_JSON, checked.dashboardJson).commit();
        if (!saved) throw new java.io.IOException("Unable to persist configuration");
    }

    public static String getStartCommand(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_START_CMD, DEFAULT_START_CMD);
    }

    public static void setStartCommand(Context context, String cmd) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_START_CMD, cmd).apply();
    }

    public static String getStopCommand(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_STOP_CMD, DEFAULT_STOP_CMD);
    }

    public static void setStopCommand(Context context, String cmd) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_STOP_CMD, cmd).apply();
    }

    public static String getHttpProxy(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_HTTP_PROXY, DEFAULT_HTTP_PROXY);
    }

    public static void setHttpProxy(Context context, String proxy) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_HTTP_PROXY, proxy).apply();
    }

    public static String getProcessName(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_PROCESS_NAME, DEFAULT_PROCESS_NAME);
    }

    public static void setProcessName(Context context, String name) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_PROCESS_NAME, name).apply();
    }

    public static String getRecordTileLabel(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_RECORD_TILE_LABEL, context.getString(R.string.record_tile_label));
    }

    public static void setRecordTileLabel(Context context, String label) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_RECORD_TILE_LABEL, label).apply();
    }

    public static String getRecordTileIcon(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_RECORD_TILE_ICON, DEFAULT_RECORD_TILE_ICON);
    }

    public static void setRecordTileIcon(Context context, String icon) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_RECORD_TILE_ICON, icon).apply();
    }

    public static String getRecordTileCommand(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_RECORD_TILE_CMD, DEFAULT_RECORD_TILE_CMD);
    }

    public static void setRecordTileCommand(Context context, String cmd) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_RECORD_TILE_CMD, cmd).apply();
    }

    public static void resetDefaults(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }
}
