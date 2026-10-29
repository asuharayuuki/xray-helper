package org.eu.suisei.xray;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings Tile to trigger Voice Recording via Samsung Voice Note Shortcut.
 * Tries native PendingIntent invocation first, falls back to root execution.
 * Zero background persistence and strict battery conservation.
 */
public class RecordTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        // 1. Try native start using official PendingIntent API for TileService
        boolean started = false;
        try {
            Intent intent = new Intent("voicenote.intent.action.START_RECORDING");
            intent.setComponent(new ComponentName(
                    "com.sec.android.app.voicenote",
                    "com.sec.android.app.voicenote.activity.ShortcutActivity"
            ));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PendingIntent pi = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(pi);
            } else {
                startLegacyActivity(intent);
            }
            started = true;
        } catch (Throwable ignored) {
            started = false;
        }

        // 2. Fall back to root execution if native fails
        if (!started) {
            new Thread(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "su", "-c",
                            "am start -a voicenote.intent.action.START_RECORDING -n com.sec.android.app.voicenote/.activity.ShortcutActivity; cmd statusbar collapse"
                    );
                    pb.start();
                } catch (Exception ignored) {
                }
            }, "Record-Starter").start();
        }

        updateTile();
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void startLegacyActivity(Intent intent) {
        // Only called below API 34, where the PendingIntent overload does not exist.
        startActivityAndCollapse(intent);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel(getString(R.string.record_tile_label));
        tile.setState(Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_mic));
        tile.setSubtitle(null);
        tile.setStateDescription(null);
        tile.updateTile();
    }
}
