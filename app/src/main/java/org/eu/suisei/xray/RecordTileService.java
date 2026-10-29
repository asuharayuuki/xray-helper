package org.eu.suisei.xray;

import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Customizable Quick Settings Tile to trigger Voice Recording or user-defined actions.
 * Label, icon, and command are configurable via AppConfig.
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

        final String cmd = AppConfig.getRecordTileCommand(this);
        if (cmd != null && !cmd.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    Process process = new ProcessBuilder("su", "-c", cmd).start();
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        new ProcessBuilder("sh", "-c", cmd).start();
                    }
                } catch (Throwable e) {
                    try {
                        new ProcessBuilder("sh", "-c", cmd).start();
                    } catch (Throwable ignored) {
                    }
                }
            }, "Record-Runner").start();
        }

        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        String label = AppConfig.getRecordTileLabel(this);
        String iconName = AppConfig.getRecordTileIcon(this);

        tile.setLabel(label != null && !label.trim().isEmpty() ? label : getString(R.string.record_tile_label));
        tile.setState(Tile.STATE_INACTIVE);
        tile.setIcon(Icon.createWithResource(this, IconHelper.getDrawableRes(iconName)));
        tile.setSubtitle(null);
        tile.setStateDescription(null);
        tile.updateTile();
    }
}
