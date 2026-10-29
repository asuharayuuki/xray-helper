package org.eu.suisei.xray;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.Locale;

/**
 * Separate Quick Settings Tile for Caffeine Mode.
 * Cycles through keep-awake durations: Off -> 5m -> 10m -> 30m -> Infinite -> Off.
 * Strict battery optimization: releases immediately on screen-off or timeout.
 */
public class CaffeineTileService extends TileService {

    private CaffeineService caffeineService;
    private boolean bound = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!bound) return;
            CaffeineService.LocalBinder binder = (CaffeineService.LocalBinder) service;
            caffeineService = binder.getService();
            caffeineService.setCallback(new CaffeineService.Callback() {
                @Override
                public void onTick(int remainingSeconds) {
                    mainHandler.post(() -> updateTileDisplay(true, remainingSeconds));
                }

                @Override
                public void onExpire() {
                    mainHandler.post(() -> updateTileDisplay(false, 0));
                }
            });

            if (caffeineService.isActive()) {
                updateTileDisplay(true, caffeineService.getRemainingSeconds());
            } else {
                updateTileDisplay(false, 0);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            caffeineService = null;
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        CaffeineService service = CaffeineService.getInstance();
        if (service != null && service.isActive()) {
            updateTileDisplay(true, service.getRemainingSeconds());
        } else {
            updateTileDisplay(false, 0);
        }

        bindIfNeeded();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        CaffeineService service = CaffeineService.getInstance();
        updateTileDisplay(service != null && service.isActive(),
                service == null ? 0 : service.getRemainingSeconds());
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        unbind();
    }

    @Override
    public void onDestroy() {
        unbind();
        super.onDestroy();
    }

    private void bindIfNeeded() {
        if (!bound) {
            bound = bindService(new Intent(this, CaffeineService.class), connection, Context.BIND_AUTO_CREATE);
        }
    }

    private void unbind() {
        if (bound) {
            if (caffeineService != null) {
                caffeineService.setCallback(null);
            }
            unbindService(connection);
            bound = false;
            caffeineService = null;
        }
    }

    @Override
    public void onClick() {
        super.onClick();

        // A bound-only service is destroyed when the panel closes. Start it explicitly
        // as a foreground service so an active timer survives unbinding.
        Intent intent = new Intent(this, CaffeineService.class);
        intent.setAction(CaffeineService.ACTION_START_NEXT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindIfNeeded();
    }

    private void updateTileDisplay(boolean active, int seconds) {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel(getString(R.string.caffeine_tile_label));

        if (active) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.local_cafe_filled));
            if (seconds == Integer.MAX_VALUE) {
                tile.setSubtitle(getString(R.string.caffeine_infinite));
                tile.setStateDescription(getString(R.string.caffeine_infinite));
            } else {
                String timeStr = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
                tile.setSubtitle(timeStr);
                tile.setStateDescription(timeStr);
            }
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setIcon(Icon.createWithResource(this, R.drawable.local_cafe_outline));
            tile.setSubtitle(getString(R.string.caffeine_off));
            tile.setStateDescription(getString(R.string.caffeine_off));
        }

        tile.updateTile();
    }
}
