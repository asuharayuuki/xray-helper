package org.eu.suisei.xray;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {

    private static final String PREFS_NAME = "xray_config";
    public static final String KEY_START_CMD = "cmd_start";
    public static final String KEY_STOP_CMD = "cmd_stop";
    public static final String KEY_HTTP_PROXY = "http_proxy";
    public static final String KEY_PROCESS_NAME = "process_name";

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

    private AppConfig() {}

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

    public static void resetDefaults(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }
}
