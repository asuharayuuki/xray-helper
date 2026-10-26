package org.eu.suisei.xray;

import android.content.ComponentName;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Active Quick Settings TileService for controlling Xray and system HTTP proxy.
 * Implements low battery consumption, zero daemon, and reliable state handling.
 */
public class XrayTileService extends TileService {

    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        // Request listening lifecycle to perform initial state synchronization
        requestListeningState(this, new ComponentName(this, XrayTileService.class));
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        // Active Tile: Only perform root query upon explicit initial sync request
        if (initialized.compareAndSet(false, true)) {
            if (busy.compareAndSet(false, true)) {
                new Thread(() -> {
                    try {
                        RootShell.Result result = RootShell.queryState();
                        applyTileState(result);
                    } catch (Exception e) {
                        applyTileState(new RootShell.Result(RootShell.ActionResult.ERROR, RootShell.State.ERROR));
                    } finally {
                        busy.set(false);
                    }
                }, "Xray-InitQuery").start();
            }
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        // Must not stop Xray when quick settings panel is closed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Must not terminate Xray on service destroy
    }

    @Override
    public void onClick() {
        super.onClick();

        // Prevent concurrent toggle operations
        if (busy.get()) {
            return;
        }

        // Require device unlock if screen is locked before root execution
        if (isLocked()) {
            unlockAndRun(this::performToggle);
            return;
        }

        performToggle();
    }

    private void performToggle() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }

        new Thread(() -> {
            try {
                RootShell.Result result = RootShell.toggle();
                applyTileState(result);
            } catch (Exception e) {
                applyTileState(new RootShell.Result(RootShell.ActionResult.ERROR, RootShell.State.ERROR));
            } finally {
                busy.set(false);
            }
        }, "Xray-Toggle").start();
    }

    private void applyTileState(RootShell.Result result) {
        mainHandler.post(() -> {
            Tile tile = getQsTile();
            if (tile == null) {
                return;
            }

            if (result.result == RootShell.ActionResult.ERROR && result.state != RootShell.State.ON) {
                tile.setLabel(getString(R.string.tile_label_normal));
                tile.setSubtitle(getString(R.string.state_error));
                tile.setState(Tile.STATE_INACTIVE);
                tile.setStateDescription(getString(R.string.state_error));
            } else {
                switch (result.state) {
                    case ON:
                        tile.setLabel(getString(R.string.tile_label_normal));
                        tile.setSubtitle(getString(R.string.state_on));
                        tile.setState(Tile.STATE_ACTIVE);
                        tile.setStateDescription(getString(R.string.state_on));
                        break;
                    case EXITED:
                        tile.setLabel(getString(R.string.tile_label_exited));
                        tile.setSubtitle(getString(R.string.state_exited));
                        tile.setState(Tile.STATE_INACTIVE);
                        tile.setStateDescription(getString(R.string.state_exited));
                        break;
                    case OFF:
                        tile.setLabel(getString(R.string.tile_label_normal));
                        tile.setSubtitle(getString(R.string.state_off));
                        tile.setState(Tile.STATE_INACTIVE);
                        tile.setStateDescription(getString(R.string.state_off));
                        break;
                    case ERROR:
                    default:
                        tile.setLabel(getString(R.string.tile_label_normal));
                        tile.setSubtitle(getString(R.string.state_error));
                        tile.setState(Tile.STATE_INACTIVE);
                        tile.setStateDescription(getString(R.string.state_error));
                        break;
                }
            }

            tile.updateTile();
        });
    }
}
