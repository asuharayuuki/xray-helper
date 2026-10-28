package org.eu.suisei.xray;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.TileService;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android 17 / Material You Expressive Dashboard Activity.
 * Dynamically renders user-configured sections, switches, and button preferences.
 */
public class MainActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isExecuting = new AtomicBoolean(false);

    private LinearProgressIndicator progressBar;
    private LinearLayout containerSections;

    // View references for live state updates
    private static class RowViewHolder {
        DashboardItem item;
        View rowRoot;
        ImageView imgIcon;
        TextView textTitle;
        TextView textSummary;
        MaterialSwitch switchWidget;
        ImageView imgChevron;
    }

    private final Map<String, RowViewHolder> itemHolders = new HashMap<>();

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
     * Ensures only Xray tile long-press opens this panel.
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

            int bottomPadding = insets.bottom + (int) (24 * getResources().getDisplayMetrics().density);
            scrollContent.setPadding(insets.left, 0, insets.right, bottomPadding);
            return windowInsets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDashboard();
        refreshDashboardStates();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        containerSections = findViewById(R.id.container_sections);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                refreshDashboardStates();
                return true;
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void renderDashboard() {
        containerSections.removeAllViews();
        itemHolders.clear();

        List<DashboardSection> sections = DashboardConfigManager.load(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        int dividerInset = (int) (72 * getResources().getDisplayMetrics().density);

        for (DashboardSection section : sections) {
            if (section.items.isEmpty()) continue;

            View sectionView = inflater.inflate(R.layout.item_dashboard_section, containerSections, false);
            TextView textSectionHeader = sectionView.findViewById(R.id.text_section_header);
            LinearLayout containerItems = sectionView.findViewById(R.id.container_section_items);

            textSectionHeader.setText(section.title);

            for (int i = 0; i < section.items.size(); i++) {
                DashboardItem item = section.items.get(i);
                View rowView = inflater.inflate(R.layout.item_dashboard_row, containerItems, false);

                RowViewHolder holder = new RowViewHolder();
                holder.item = item;
                holder.rowRoot = rowView.findViewById(R.id.row_root);
                holder.imgIcon = rowView.findViewById(R.id.img_icon);
                holder.textTitle = rowView.findViewById(R.id.text_title);
                holder.textSummary = rowView.findViewById(R.id.text_summary);
                holder.switchWidget = rowView.findViewById(R.id.switch_widget);
                holder.imgChevron = rowView.findViewById(R.id.img_chevron);

                holder.imgIcon.setImageResource(IconHelper.getDrawableRes(item.icon));
                holder.textTitle.setText(item.title);
                holder.textSummary.setVisibility(View.GONE);

                boolean isSwitch = DashboardItem.TYPE_SWITCH.equalsIgnoreCase(item.type);
                if (isSwitch) {
                    holder.switchWidget.setVisibility(View.VISIBLE);
                    holder.imgChevron.setVisibility(View.GONE);
                    holder.rowRoot.setOnClickListener(v -> onSwitchItemClicked(holder));
                } else {
                    holder.switchWidget.setVisibility(View.GONE);
                    holder.imgChevron.setVisibility(View.VISIBLE);
                    holder.rowRoot.setOnClickListener(v -> onButtonItemClicked(holder));
                }

                itemHolders.put(item.id, holder);
                containerItems.addView(rowView);

                // Add subtle divider between items inside the same card
                if (i < section.items.size() - 1) {
                    MaterialDivider divider = new MaterialDivider(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    divider.setLayoutParams(lp);
                    divider.setDividerInsetStart(dividerInset);
                    containerItems.addView(divider);
                }
            }

            containerSections.addView(sectionView);
        }
    }

    private void onSwitchItemClicked(RowViewHolder holder) {
        if (holder.item.isCore) {
            boolean nextState = !holder.switchWidget.isChecked();
            setBusy(true);
            new Thread(() -> {
                if (nextState) {
                    RootShell.startService(MainActivity.this);
                } else {
                    RootShell.stopService(MainActivity.this);
                }
                XrayTileService.requestRefresh(MainActivity.this);
                mainHandler.post(() -> {
                    setBusy(false);
                    refreshDashboardStates();
                });
            }, "Core-Toggle").start();
        } else {
            boolean nextState = !holder.switchWidget.isChecked();
            String command = nextState ? holder.item.onCommand : holder.item.offCommand;
            executeCommandAsync(command, () -> refreshDashboardStates());
        }
    }

    private void onButtonItemClicked(RowViewHolder holder) {
        executeCommandAsync(holder.item.onCommand, () -> refreshDashboardStates());
    }

    private void refreshDashboardStates() {
        if (!isExecuting.compareAndSet(false, true)) {
            return;
        }

        setBusy(true);

        new Thread(() -> {
            try {
                // 1. Evaluate Core Status
                RootShell.DetailedStatus coreStatus = RootShell.queryDetailedStatus(MainActivity.this);
                String configuredProxy = AppConfig.getHttpProxy(MainActivity.this);

                // Determine core 4-state summary (Requirement: No PID, standard commas)
                boolean isProxyActive = configuredProxy.equals(coreStatus.httpProxy);
                boolean isCoreRunning = (coreStatus.pid != null && !coreStatus.pid.isEmpty());

                final boolean coreSwitchChecked = isProxyActive; // switch represents proxy engagement
                final String coreSummaryText;
                if (!coreStatus.isRootGranted) {
                    coreSummaryText = getString(R.string.state_root_denied);
                } else if (isCoreRunning && isProxyActive) {
                    coreSummaryText = getString(R.string.state_core_on); // "Running, proxy active"
                } else if (isCoreRunning) {
                    coreSummaryText = getString(R.string.state_core_running_proxy_off); // "Running, proxy disabled"
                } else if (isProxyActive) {
                    coreSummaryText = getString(R.string.state_core_exited); // "Stopped, proxy active"
                } else {
                    coreSummaryText = getString(R.string.state_core_off); // "Stopped, proxy disabled"
                }

                // 2. Evaluate other items
                Map<String, Boolean> switchStates = new HashMap<>();
                Map<String, String> summaries = new HashMap<>();
                Map<String, Boolean> disabledStates = new HashMap<>();

                for (RowViewHolder holder : itemHolders.values()) {
                    DashboardItem item = holder.item;
                    if (item.isCore) {
                        continue;
                    }

                    // Check switch state
                    if (DashboardItem.TYPE_SWITCH.equalsIgnoreCase(item.type)) {
                        if (item.stateCheckCommand != null && !item.stateCheckCommand.trim().isEmpty()) {
                            boolean on = (executeCheckExitCode(item.stateCheckCommand) == 0);
                            switchStates.put(item.id, on);
                        }
                    }

                    // Check disable condition
                    if (item.disableConditionCommand != null && !item.disableConditionCommand.trim().isEmpty()) {
                        boolean disabled = (executeCheckExitCode(item.disableConditionCommand) == 0);
                        disabledStates.put(item.id, disabled);
                    }

                    // Check summary command
                    if (item.summaryCommand != null && !item.summaryCommand.trim().isEmpty()) {
                        String summary = executeGetOutput(item.summaryCommand);
                        summaries.put(item.id, summary);
                    }
                }

                // Apply to UI
                mainHandler.post(() -> {
                    for (RowViewHolder holder : itemHolders.values()) {
                        DashboardItem item = holder.item;
                        if (item.isCore) {
                            holder.switchWidget.setChecked(coreSwitchChecked);
                            bindSummary(holder.textSummary, coreSummaryText);
                        } else {
                            if (switchStates.containsKey(item.id)) {
                                holder.switchWidget.setChecked(Boolean.TRUE.equals(switchStates.get(item.id)));
                            }
                            if (disabledStates.containsKey(item.id)) {
                                boolean isDisabled = Boolean.TRUE.equals(disabledStates.get(item.id));
                                holder.rowRoot.setEnabled(!isDisabled);
                                holder.rowRoot.setAlpha(isDisabled ? 0.45f : 1.0f);
                            } else {
                                holder.rowRoot.setEnabled(true);
                                holder.rowRoot.setAlpha(1.0f);
                            }
                            if (summaries.containsKey(item.id)) {
                                bindSummary(holder.textSummary, summaries.get(item.id));
                            } else {
                                holder.textSummary.setVisibility(View.GONE);
                            }
                        }
                    }

                    setBusy(false);
                    isExecuting.set(false);
                    XrayTileService.requestRefresh(MainActivity.this);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setBusy(false);
                    isExecuting.set(false);
                });
            }
        }, "Dashboard-StateEvaluator").start();
    }

    /**
     * Requirement: If summary text is null or empty, View.GONE.
     */
    private void bindSummary(TextView textView, String text) {
        if (text != null && !text.trim().isEmpty()) {
            textView.setText(text.trim());
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private int executeCheckExitCode(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return 1;
            }
            return process.exitValue();
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return 1;
        }
    }

    private String executeGetOutput(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(line.trim());
                    if (sb.length() > 100) break;
                }
            }

            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            return sb.toString().trim();
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return "";
        }
    }

    private void executeCommandAsync(String command, Runnable onComplete) {
        if (command == null || command.trim().isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        setBusy(true);

        new Thread(() -> {
            Process process = null;
            try {
                process = new ProcessBuilder("/system/bin/sh", "-c", command)
                        .redirectErrorStream(true)
                        .start();

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(line);
                        if (sb.length() > 2000) break;
                    }
                }

                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }

                String output = sb.toString().trim();
                if (!output.isEmpty()) {
                    if (output.length() > 200) output = output.substring(0, 200);
                    final String display = output;
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, display, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                if (process != null) process.destroyForcibly();
                String err = e.getMessage();
                if (err != null && !err.trim().isEmpty()) {
                    if (err.length() > 200) err = err.substring(0, 200);
                    final String display = err.trim();
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, display, Toast.LENGTH_LONG).show());
                }
            } finally {
                mainHandler.post(() -> {
                    setBusy(false);
                    if (onComplete != null) onComplete.run();
                });
            }
        }, "CustomAction-Executor").start();
    }
}
