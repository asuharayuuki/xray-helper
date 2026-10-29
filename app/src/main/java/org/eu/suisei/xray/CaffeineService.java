package org.eu.suisei.xray;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.quicksettings.TileService;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

/**
 * Lightweight foreground service holding a WakeLock while Caffeine is active.
 * Shows an active notification to prevent being stopped by the system.
 * Stops automatically and removes notification when expired, cancelled, or screen turns off.
 * Strict battery priority: zero resource consumption when inactive.
 */
public class CaffeineService extends Service {

    public static final String ACTION_START_NEXT = "org.eu.suisei.xray.action.CAFFEINE_NEXT";
    public static final String ACTION_STOP = "org.eu.suisei.xray.action.CAFFEINE_STOP";
    private static final String CHANNEL_ID = "caffeine_foreground_channel";
    private static final int NOTIFICATION_ID = 2001;

    public static final int[] DURATIONS = new int[] {
            5 * 60,
            10 * 60,
            30 * 60,
            Integer.MAX_VALUE
    };

    public interface Callback {
        void onTick(int remainingSeconds);
        void onExpire();
    }

    private static CaffeineService sInstance;

    public static CaffeineService getInstance() {
        return sInstance;
    }

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;
    private CountDownTimer countDownTimer;
    private int durationSeconds = 0;
    private int remainingSeconds = 0;
    private Callback callback;
    private BroadcastReceiver screenOffReceiver;

    public class LocalBinder extends Binder {
        public CaffeineService getService() {
            return CaffeineService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "org.eu.suisei.xray:caffeine"
            );
            wakeLock.setReferenceCounted(false);
        }

        createNotificationChannel();

        // Conserve battery: auto release WakeLock immediately when screen turns off.
        // Explicitly specify RECEIVER_NOT_EXPORTED for Android 14+ (API 34/37) safety.
        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    stopCaffeine();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.caffeine_tile_label),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.caffeine_notification_channel_desc));
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(int seconds) {
        Intent stopIntent = new Intent(this, CaffeineService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText;
        if (seconds == Integer.MAX_VALUE) {
            contentText = getString(R.string.caffeine_notification_text_infinite);
        } else {
            String timeStr = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
            contentText = getString(R.string.caffeine_notification_text_timed, timeStr);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.local_cafe_filled)
                .setContentTitle(getString(R.string.caffeine_notification_title))
                .setContentText(contentText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, getString(R.string.caffeine_notification_stop), stopPendingIntent)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP.equals(intent.getAction())) {
                stopCaffeine();
                return START_NOT_STICKY;
            }
            if (ACTION_START_NEXT.equals(intent.getAction())) {
                cycleNext();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public boolean isActive() {
        return wakeLock != null && wakeLock.isHeld();
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void cycleNext() {
        if (!isActive()) {
            startCaffeine(DURATIONS[0]);
        } else {
            int nextIndex = -1;
            for (int i = 0; i < DURATIONS.length; i++) {
                if (DURATIONS[i] == durationSeconds) {
                    nextIndex = i + 1;
                    break;
                }
            }

            if (nextIndex >= 0 && nextIndex < DURATIONS.length) {
                startCaffeine(DURATIONS[nextIndex]);
            } else {
                stopCaffeine();
            }
        }
    }

    public void startCaffeine(int seconds) {
        if (wakeLock == null) return;
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        durationSeconds = seconds;
        remainingSeconds = seconds;

        // Reacquire when changing duration so the system timeout matches the new selection.
        if (wakeLock.isHeld()) wakeLock.release();
        if (seconds == Integer.MAX_VALUE) {
            acquireUntilScreenOff();
        } else {
            wakeLock.acquire((seconds + 5L) * 1000L);
        }

        // Show foreground notification to prevent being killed by system
        Notification notification = buildNotification(seconds);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable e) {
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Throwable ignored) {
            }
        }

        if (seconds == Integer.MAX_VALUE) {
            if (callback != null) {
                callback.onTick(Integer.MAX_VALUE);
            }
        } else {
            countDownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
                @Override
                public void onTick(long millisUntilFinished) {
                    remainingSeconds = (int) (millisUntilFinished / 1000L);
                    if (callback != null) {
                        callback.onTick(remainingSeconds);
                    }
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null && isActive()) {
                        nm.notify(NOTIFICATION_ID, buildNotification(remainingSeconds));
                    }
                }

                @Override
                public void onFinish() {
                    stopCaffeine();
                }
            }.start();
        }
    }

    public void stopCaffeine() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        durationSeconds = 0;
        remainingSeconds = 0;

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (callback != null) {
            callback.onExpire();
        }

        // Remove foreground notification immediately when stopped
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        // Notify tile to update display
        try {
            TileService.requestListeningState(
                    this,
                    new ComponentName(this, CaffeineTileService.class)
            );
        } catch (Exception ignored) {
        }

        stopSelf();
    }

    @android.annotation.SuppressLint("WakelockTimeout")
    private void acquireUntilScreenOff() {
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        callback = null;
        sInstance = null;
        if (screenOffReceiver != null) {
            try {
                unregisterReceiver(screenOffReceiver);
            } catch (Exception ignored) {}
            screenOffReceiver = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }
}
