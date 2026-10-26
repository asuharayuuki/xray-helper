package org.eu.suisei.xray;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MD3 Management UI opened by long-pressing the Xray Quick Settings tile.
 * Zero background persistence and ultra-low battery consumption.
 */
public class MainActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);

    private LinearProgressIndicator progressBar;
    private TextView textBadgeState;
    private TextView textStateDesc;
    private TextView textPid;
    private TextView textProxy;
    private MaterialButton btnStart;
    private MaterialButton btnStop;
    private MaterialButton btnRestart;
    private MaterialButton btnDisableAdb;

    private boolean currentAdbEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!shouldHandleIntent(getIntent())) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupWindowInsets();
        initViews();
        setupToolbar();
        setupActions();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!shouldHandleIntent(intent)) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    /**
     * Ensures only the Xray tile's long-press opens this activity.
     * Long-pressing Caffeine or Record will not open the Xray panel.
     */
    private boolean shouldHandleIntent(Intent intent) {
        if (intent == null) return true;
        if (TileService.ACTION_QS_TILE_PREFERENCES.equals(intent.getAction())) {
            ComponentName component = null;
            try {
                Object extra = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
                if (extra instanceof ComponentName) {
                    component = (ComponentName) extra;
                } else {
                    String extraStr = intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME);
                    if (extraStr != null) {
                        component = ComponentName.unflattenFromString(extraStr);
                    }
                }
            } catch (Exception ignored) {
            }

            if (component != null && !XrayTileService.class.getName().equals(component.getClassName())) {
                return false;
            }
        }
        return true;
    }

    private void setupWindowInsets() {
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        insetsController.setAppearanceLightStatusBars(!isNight);
        insetsController.setAppearanceLightNavigationBars(!isNight);

        View appbar = findViewById(R.id.appbar);
        View scrollContent = findViewById(R.id.scroll_content);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            appbar.setPadding(insets.left, insets.top, insets.right, 0);

            int horizontalPadding = (int) (16 * getResources().getDisplayMetrics().density);
            int topPadding = (int) (8 * getResources().getDisplayMetrics().density);
            int bottomPadding = insets.bottom + (int) (16 * getResources().getDisplayMetrics().density);

            scrollContent.setPadding(
                    insets.left + horizontalPadding,
                    topPadding,
                    insets.right + horizontalPadding,
                    bottomPadding
            );
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStatus();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        textBadgeState = findViewById(R.id.text_badge_state);
        textStateDesc = findViewById(R.id.text_state_desc);
        textPid = findViewById(R.id.text_pid);
        textProxy = findViewById(R.id.text_proxy);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnRestart = findViewById(R.id.btn_restart);
        btnDisableAdb = findViewById(R.id.btn_disable_adb);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                loadStatus();
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        btnStart.setOnClickListener(v -> executeAction(RootShell::startService));
        btnStop.setOnClickListener(v -> executeAction(RootShell::stopService));
        btnRestart.setOnClickListener(v -> executeAction(RootShell::restartService));
        btnDisableAdb.setOnClickListener(v -> executeAction(RootShell::disableDebugging));
    }

    private void loadStatus() {
        if (isExecuting.get()) {
            return;
        }
        executeAction(RootShell::queryDetailedStatus);
    }

    @FunctionalInterface
    private interface ActionSupplier {
        RootShell.DetailedStatus execute();
    }

    private void executeAction(ActionSupplier action) {
        if (!isExecuting.compareAndSet(false, true)) {
            return;
        }

        setBusy(true);

        new Thread(() -> {
            RootShell.DetailedStatus status;
            try {
                status = action.execute();
            } catch (Exception e) {
                status = new RootShell.DetailedStatus(
                        RootShell.State.ERROR,
                        "Unknown",
                        null,
                        false,
                        false
                );
            }

            final RootShell.DetailedStatus finalStatus = status;
            mainHandler.post(() -> {
                bindStatus(finalStatus);
                setBusy(false);
                isExecuting.set(false);

                // Request QS tile refresh to keep tile in sync
                try {
                    TileService.requestListeningState(
                            MainActivity.this,
                            new ComponentName(MainActivity.this, XrayTileService.class)
                    );
                } catch (Exception ignored) {
                }
            });
        }, "Xray-UI-Action").start();
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnStart.setEnabled(!busy);
        btnStop.setEnabled(!busy);
        btnRestart.setEnabled(!busy);
        btnDisableAdb.setEnabled(!busy && currentAdbEnabled);
    }

    private void bindStatus(RootShell.DetailedStatus status) {
        currentAdbEnabled = status.isAdbEnabled;
        btnDisableAdb.setEnabled(currentAdbEnabled);

        if (!status.isRootGranted) {
            setBadge(getString(R.string.state_root_denied), Color.parseColor("#B00020"));
            textStateDesc.setText(R.string.desc_error);
        } else {
            switch (status.state) {
                case ON:
                    setBadge(getString(R.string.state_on), Color.parseColor("#2E7D32"));
                    textStateDesc.setText(R.string.desc_on);
                    break;
                case EXITED:
                    setBadge(getString(R.string.state_exited), Color.parseColor("#E65100"));
                    textStateDesc.setText(R.string.desc_exited);
                    break;
                case OFF:
                    setBadge(getString(R.string.state_off), Color.parseColor("#616161"));
                    textStateDesc.setText(R.string.desc_off);
                    break;
                case ERROR:
                default:
                    setBadge(getString(R.string.state_error), Color.parseColor("#C62828"));
                    textStateDesc.setText(R.string.desc_error);
                    break;
            }
        }

        // PID
        if (status.pid != null && !status.pid.isEmpty()) {
            textPid.setText(status.pid);
            textPid.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            textPid.setText(R.string.status_not_running);
            textPid.setTextColor(Color.parseColor("#757575"));
        }

        // Proxy
        if (status.httpProxy != null && !status.httpProxy.isEmpty()) {
            if ("127.0.0.1:2080".equals(status.httpProxy)) {
                textProxy.setText(R.string.status_active);
                textProxy.setTextColor(Color.parseColor("#2E7D32"));
            } else if (":0".equals(status.httpProxy) || "null".equalsIgnoreCase(status.httpProxy)) {
                textProxy.setText(R.string.status_disabled);
                textProxy.setTextColor(Color.parseColor("#757575"));
            } else {
                textProxy.setText(status.httpProxy);
                textProxy.setTextColor(Color.parseColor("#E65100"));
            }
        } else {
            textProxy.setText(R.string.status_disabled);
            textProxy.setTextColor(Color.parseColor("#757575"));
        }
    }

    private void setBadge(String text, int color) {
        textBadgeState.setText(text);
        if (textBadgeState.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) textBadgeState.getBackground()).setColor(color);
        } else {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(100f);
            drawable.setColor(color);
            textBadgeState.setBackground(drawable);
        }
    }
}
