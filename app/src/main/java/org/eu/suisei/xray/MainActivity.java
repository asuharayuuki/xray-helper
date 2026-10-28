package org.eu.suisei.xray;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.util.TypedValue;
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
import com.google.android.material.color.DynamicColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MD3 Management UI with Scheme B (Central Power Toggle).
 * Zero background persistence and ultra-low battery consumption.
 */
public class MainActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);

    private LinearProgressIndicator progressBar;
    private MaterialCardView cardPowerOuter;
    private MaterialButton btnPowerToggle;
    private TextView textStatusTitle;
    private TextView textStatusSubtitle;
    private MaterialButton btnRestart;
    private LinearLayout containerCustomActions;

    private RootShell.State currentState = RootShell.State.OFF;
    private String currentPid = null;
    private long currentStartTimeMs = 0L;

    private final Runnable uptimeTicker = new Runnable() {
        @Override
        public void run() {
            if (currentState == RootShell.State.ON && currentPid != null) {
                updateUptimeText();
                mainHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
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

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(uptimeTicker);
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        cardPowerOuter = findViewById(R.id.card_power_outer);
        btnPowerToggle = findViewById(R.id.btn_power_toggle);
        textStatusTitle = findViewById(R.id.text_status_title);
        textStatusSubtitle = findViewById(R.id.text_status_subtitle);
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
                refreshCustomActionConditions();
                return true;
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupActions() {
        btnPowerToggle.setOnClickListener(v -> {
            if (currentState == RootShell.State.ON) {
                executeAction(() -> RootShell.stopService(MainActivity.this));
            } else {
                executeAction(() -> RootShell.startService(MainActivity.this));
            }
        });

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
                        0L,
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
        btnPowerToggle.setEnabled(!busy);
        btnRestart.setEnabled(!busy);
    }

    private void bindStatus(RootShell.DetailedStatus status) {
        currentState = status.state;
        currentPid = status.pid;

        if (!status.isRootGranted) {
            textStatusTitle.setText(R.string.state_root_denied);
            textStatusTitle.setTextColor(Color.parseColor("#B00020"));
            textStatusSubtitle.setText(R.string.status_not_running);

            cardPowerOuter.setCardBackgroundColor(Color.parseColor("#2A1515"));
            cardPowerOuter.setStrokeColor(Color.parseColor("#B00020"));
            btnPowerToggle.setIconTint(ColorStateList.valueOf(Color.parseColor("#EF5350")));
            mainHandler.removeCallbacks(uptimeTicker);
        } else {
            switch (status.state) {
                case ON:
                    textStatusTitle.setText(R.string.state_on); // "已连接"
                    textStatusTitle.setTextColor(Color.parseColor("#2E7D32"));

                    cardPowerOuter.setCardBackgroundColor(Color.parseColor("#152B18"));
                    cardPowerOuter.setStrokeColor(Color.parseColor("#4CAF50"));
                    btnPowerToggle.setIconTint(ColorStateList.valueOf(Color.parseColor("#81C784")));

                    if (status.startTimeMs > 0) {
                        currentStartTimeMs = status.startTimeMs;
                    } else if (currentStartTimeMs <= 0) {
                        currentStartTimeMs = System.currentTimeMillis();
                    }

                    updateUptimeText();
                    mainHandler.removeCallbacks(uptimeTicker);
                    mainHandler.postDelayed(uptimeTicker, 1000);
                    break;

                case EXITED:
                    textStatusTitle.setText(R.string.state_exited); // "异常退出"
                    textStatusTitle.setTextColor(Color.parseColor("#E65100"));
                    textStatusSubtitle.setText(R.string.status_not_running);

                    cardPowerOuter.setCardBackgroundColor(Color.parseColor("#2B2115"));
                    cardPowerOuter.setStrokeColor(Color.parseColor("#FB8C00"));
                    btnPowerToggle.setIconTint(ColorStateList.valueOf(Color.parseColor("#FFB74D")));

                    currentStartTimeMs = 0L;
                    mainHandler.removeCallbacks(uptimeTicker);
                    break;

                case OFF:
                    textStatusTitle.setText(R.string.state_off); // "未连接"
                    textStatusTitle.setTextColor(getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant));
                    textStatusSubtitle.setText(R.string.status_not_running);

                    cardPowerOuter.setCardBackgroundColor(getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh));
                    cardPowerOuter.setStrokeColor(getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant));
                    btnPowerToggle.setIconTint(ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorOutline)));

                    currentStartTimeMs = 0L;
                    mainHandler.removeCallbacks(uptimeTicker);
                    break;

                case ERROR:
                default:
                    textStatusTitle.setText(R.string.state_error); // "连接失败"
                    textStatusTitle.setTextColor(Color.parseColor("#C62828"));
                    textStatusSubtitle.setText(R.string.status_not_running);

                    cardPowerOuter.setCardBackgroundColor(Color.parseColor("#2A1515"));
                    cardPowerOuter.setStrokeColor(Color.parseColor("#C62828"));
                    btnPowerToggle.setIconTint(ColorStateList.valueOf(Color.parseColor("#EF5350")));

                    currentStartTimeMs = 0L;
                    mainHandler.removeCallbacks(uptimeTicker);
                    break;
            }
        }
        refreshCustomActionConditions();
    }

    private void updateUptimeText() {
        if (currentPid == null || currentPid.isEmpty()) {
            textStatusSubtitle.setText(R.string.status_not_running);
            return;
        }

        if (currentStartTimeMs <= 0) {
            textStatusSubtitle.setText("PID " + currentPid);
            return;
        }

        long diffSec = Math.max(0, (System.currentTimeMillis() - currentStartTimeMs) / 1000);
        long hours = diffSec / 3600;
        long minutes = (diffSec % 3600) / 60;
        long seconds = diffSec % 60;
        String timeStr;
        if (hours > 0) {
            timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
        textStatusSubtitle.setText(getString(R.string.status_uptime_pid, currentPid, timeStr));
    }

    private int getColorFromAttr(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    // ==========================================
    // Custom Buttons Execution & Condition Evaluation
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
            refreshCustomActionConditions();
        }
    }

    private void refreshCustomActionConditions() {
        List<CustomAction> actions = CustomActionManager.load(this);
        int count = containerCustomActions.getChildCount();
        if (count != actions.size()) {
            return;
        }

        new Thread(() -> {
            for (int i = 0; i < actions.size(); i++) {
                CustomAction action = actions.get(i);
                final int index = i;
                if (action.conditionCommand != null && !action.conditionCommand.trim().isEmpty()) {
                    boolean isTrue = evaluateCondition(action.conditionCommand);
                    // "当命令为真时，该框禁用"
                    mainHandler.post(() -> {
                        if (index < containerCustomActions.getChildCount()) {
                            View child = containerCustomActions.getChildAt(index);
                            if (child instanceof MaterialButton) {
                                ((MaterialButton) child).setEnabled(!isTrue);
                            }
                        }
                    });
                }
            }
        }, "Condition-Checker").start();
    }

    private boolean evaluateCondition(String conditionCommand) {
        if (conditionCommand == null || conditionCommand.trim().isEmpty()) {
            return false;
        }
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", conditionCommand)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return false;
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
            } finally {
                // Refresh button disable conditions immediately after execution
                mainHandler.post(MainActivity.this::refreshCustomActionConditions);
            }
        }, "CustomAction-Executor").start();
    }
}
