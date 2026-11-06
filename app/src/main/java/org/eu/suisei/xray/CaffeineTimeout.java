package org.eu.suisei.xray;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Samsung disables background screen wake locks as abusive, even with a foreground service. */
public final class CaffeineTimeout extends BroadcastReceiver {
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    static boolean isRequired() {
        return "samsung".equalsIgnoreCase(Build.MANUFACTURER);
    }

    static ScreenTimeoutOverride create(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("caffeine_timeout", Context.MODE_PRIVATE);
        return new ScreenTimeoutOverride(new ScreenTimeoutOverride.Storage() {
            public Integer read() {
                String value = Settings.System.getString(app.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
                return value == null ? null : Integer.valueOf(value);
            }

            public void write(Integer value) throws Exception {
                int user = Process.myUid() / 100000;
                String command = "settings --user " + user + (value == null
                        ? " delete system screen_off_timeout"
                        : " put system screen_off_timeout " + value);
                CommandRunner.Result result = CommandRunner.run(new ProcessBuilder("su", "-c", command), 5000, 2048);
                if (result.timedOut || result.exitCode != 0) {
                    throw new IllegalStateException("Cannot change screen timeout with root");
                }
            }

            public boolean hasBackup() { return prefs.contains("applied"); }
            public Integer original() { return prefs.contains("original") ? prefs.getInt("original", 30000) : null; }
            public int applied() { return prefs.getInt("applied", 0); }
            public void backup(Integer original, int applied) {
                SharedPreferences.Editor editor = prefs.edit().putInt("applied", applied);
                if (original == null) editor.remove("original"); else editor.putInt("original", original);
                if (!editor.commit()) throw new IllegalStateException("Cannot save screen timeout backup");
            }
            public void clear() {
                if (!prefs.edit().clear().commit()) throw new IllegalStateException("Cannot clear screen timeout backup");
            }
        });
    }

    static void restore(Context context) {
        try {
            create(context).restore();
        } catch (Exception e) {
            Log.e("CaffeineTimeout", "Could not restore screen timeout; backup retained for retry", e);
        }
    }

    public static void recover(Context context) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            CaffeineService service = CaffeineService.getInstance();
            if (service == null || !service.isActive()) restore(app);
        });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        PendingResult pending = goAsync();
        WORKER.execute(() -> {
            try { restore(context); } finally { pending.finish(); }
        });
    }
}
