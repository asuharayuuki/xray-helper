package org.eu.suisei.xray;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.quicksettings.TileService;

/**
 * Lightweight background service holding a WakeLock while Caffeine is active.
 * Stops automatically when expired, cancelled, or screen turns off.
 * Strict battery priority: zero resource consumption when inactive.
 */
public class CaffeineService extends Service {

    public static final String ACTION_START_NEXT = "org.eu.suisei.xray.action.CAFFEINE_NEXT";

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

        // Conserve battery: auto release WakeLock immediately when screen turns off
        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    stopCaffeine();
                }
            }
        };
        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START_NEXT.equals(intent.getAction())) {
            cycleNext();
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

        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
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

    @Override
    public void onDestroy() {
        super.onDestroy();
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
    }
}
