package org.eu.suisei.xray;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MD3 Management UI opened from launcher icon or long-pressing the Xray Quick Settings tile.
 * Zero background persistence and ultra-low battery consumption.
 */
public class MainActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);

    private LinearProgressIndicator progressBar;
    private TextView textBadgeState;
    private MaterialCardView cardPid;
    private TextView textPid;
    private MaterialButton btnStart;
    private MaterialButton btnStop;
    private MaterialButton btnRestart;
    private LinearLayout containerCustomActions;

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
     * Ensures long-pressing Caffeine or Record will not open the Xray panel.
     */
    private boolean shouldHandleIntent(Intent intent) {
        if (intent == null) return true;
        if (TileService.ACTION_QS_TILE_PREFERENCES.equals(intent.getAction())) {
            ComponentName component = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    component = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName.class);
                } else {
                    Object extra = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);
                    if (extra instanceof ComponentName) {
                        component = (ComponentName) extra;
                    }
                }
                if (component == null) {
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
        renderCustomActions();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        textBadgeState = findViewById(R.id.text_badge_state);
        cardPid = findViewById(R.id.card_pid);
        textPid = findViewById(R.id.text_pid);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnRestart = findViewById(R.id.btn_restart);

        containerCustomActions = findViewById(R.id.container_custom_actions);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                loadStatus();
                return true;
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        btnStart.setOnClickListener(v -> executeAction(() -> RootShell.startService(MainActivity.this)));
        btnStop.setOnClickListener(v -> executeAction(() -> RootShell.stopService(MainActivity.this)));
        btnRestart.setOnClickListener(v -> executeAction(() -> RootShell.restartService(MainActivity.this)));
    }

    private void loadStatus() {
        if (isExecuting.get()) {
            return;
        }
        executeAction(() -> RootShell.queryDetailedStatus(MainActivity.this));
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

                // Synchronize QS tile reliably without polling daemon
                XrayTileService.requestRefresh(MainActivity.this);
            });
        }, "Xray-UI-Action").start();
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnStart.setEnabled(!busy);
        btnStop.setEnabled(!busy);
        btnRestart.setEnabled(!busy);
    }

    private void bindStatus(RootShell.DetailedStatus status) {
        if (!status.isRootGranted) {
            setBadge(getString(R.string.state_root_denied).toUpperCase(), Color.parseColor("#B00020"));
            cardPid.setVisibility(View.GONE);
        } else {
            switch (status.state) {
                case ON:
                    setBadge(getString(R.string.state_on).toUpperCase(), Color.parseColor("#2E7D32"));
                    if (status.pid != null && !status.pid.isEmpty()) {
                        textPid.setText("PID: " + status.pid);
                        cardPid.setVisibility(View.VISIBLE);
                    } else {
                        cardPid.setVisibility(View.GONE);
                    }
                    break;
                case EXITED:
                    setBadge(getString(R.string.state_exited).toUpperCase(), Color.parseColor("#E65100"));
                    cardPid.setVisibility(View.GONE);
                    break;
                case OFF:
                    setBadge(getString(R.string.state_off).toUpperCase(), Color.parseColor("#616161"));
                    cardPid.setVisibility(View.GONE);
                    break;
                case ERROR:
                default:
                    setBadge(getString(R.string.state_error).toUpperCase(), Color.parseColor("#C62828"));
                    cardPid.setVisibility(View.GONE);
                    break;
            }
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

    // ==========================================
    // Custom Buttons Execution
    // ==========================================

    private void renderCustomActions() {
        containerCustomActions.removeAllViews();
        List<CustomAction> actions = CustomActionManager.load(this);

        if (!actions.isEmpty()) {
            LayoutInflater inflater = LayoutInflater.from(this);
            for (CustomAction action : actions) {
                MaterialButton btnAction = (MaterialButton) inflater.inflate(R.layout.item_custom_action, containerCustomActions, false);
                btnAction.setText(action.name);
                btnAction.setOnClickListener(v -> executeCustomCommand(action.command));
                containerCustomActions.addView(btnAction);
            }
        }
    }

    private void executeCustomCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        new Thread(() -> {
            Process process = null;
            try {
                process = new ProcessBuilder("/system/bin/sh", "-c", command)
                        .redirectErrorStream(true)
                        .start();

                StringBuilder sb = new StringBuilder();
                final Process proc = process;

                // Read output concurrently to prevent pipe buffer stalls
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(line);
                        // Prevent excessive memory consumption
                        if (sb.length() > 2000) {
                            break;
                        }
                    }
                }

                // 10s execution timeout
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }

                String output = sb.toString().trim();
                if (!output.isEmpty()) {
                    // Truncate to maximum 200 characters per user requirement
                    if (output.length() > 200) {
                        output = output.substring(0, 200);
                    }
                    final String displayOutput = output;
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, displayOutput, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                if (process != null) {
                    process.destroyForcibly();
                }
                String err = e.getMessage();
                if (err != null && !err.trim().isEmpty()) {
                    if (err.length() > 200) {
                        err = err.substring(0, 200);
                    }
                    final String displayErr = err.trim();
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, displayErr, Toast.LENGTH_LONG).show());
                }
            }
        }, "CustomAction-Executor").start();
    }
}
