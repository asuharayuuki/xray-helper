package org.eu.suisei.xray;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.UUID;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText editStartCmd;
    private TextInputEditText editStopCmd;
    private TextInputEditText editHttpProxy;
    private TextInputEditText editProcessName;
    private MaterialButton btnSaveCommands;

    private MaterialButton btnAddSection;
    private LinearLayout containerSettingsSections;
    private MaterialButton btnResetAllDefaults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupWindowInsets();
        setupToolbar();
        initViews();
        loadCommandConfig();
        renderSections();
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
            scrollContent.setPadding(insets.left + (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (8 * getResources().getDisplayMetrics().density),
                    insets.right + (int) (16 * getResources().getDisplayMetrics().density),
                    bottomPadding);
            return windowInsets;
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        editStartCmd = findViewById(R.id.edit_start_cmd);
        editStopCmd = findViewById(R.id.edit_stop_cmd);
        editHttpProxy = findViewById(R.id.edit_http_proxy);
        editProcessName = findViewById(R.id.edit_process_name);
        btnSaveCommands = findViewById(R.id.btn_save_commands);

        btnAddSection = findViewById(R.id.btn_add_section);
        containerSettingsSections = findViewById(R.id.container_settings_sections);
        btnResetAllDefaults = findViewById(R.id.btn_reset_all_defaults);

        btnSaveCommands.setOnClickListener(v -> saveCommandConfig());
        btnAddSection.setOnClickListener(v -> showSectionDialog(null));
        btnResetAllDefaults.setOnClickListener(v -> resetAllToDefaults());
    }

    private void loadCommandConfig() {
        editStartCmd.setText(AppConfig.getStartCommand(this));
        editStopCmd.setText(AppConfig.getStopCommand(this));
        editHttpProxy.setText(AppConfig.getHttpProxy(this));
        editProcessName.setText(AppConfig.getProcessName(this));
    }

    private void saveCommandConfig() {
        String startCmd = editStartCmd.getText() != null ? editStartCmd.getText().toString().trim() : "";
        String stopCmd = editStopCmd.getText() != null ? editStopCmd.getText().toString().trim() : "";
        String proxy = editHttpProxy.getText() != null ? editHttpProxy.getText().toString().trim() : "";
        String procName = editProcessName.getText() != null ? editProcessName.getText().toString().trim() : "";

        if (startCmd.isEmpty() || stopCmd.isEmpty() || proxy.isEmpty() || procName.isEmpty()) {
            Toast.makeText(this, "Commands cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        AppConfig.setStartCommand(this, startCmd);
        AppConfig.setStopCommand(this, stopCmd);
        AppConfig.setHttpProxy(this, proxy);
        AppConfig.setProcessName(this, procName);

        Toast.makeText(this, R.string.msg_commands_saved, Toast.LENGTH_SHORT).show();
    }

    private void resetAllToDefaults() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.btn_reset_defaults)
                .setMessage("Reset all Xray commands and dashboard layout to defaults?")
                .setPositiveButton("Reset", (d, w) -> {
                    AppConfig.resetDefaults(this);
                    loadCommandConfig();
                    List<DashboardSection> defaults = DashboardConfigManager.createDefaults();
                    DashboardConfigManager.save(this, defaults);
                    renderSections();
                    Toast.makeText(this, R.string.msg_defaults_restored, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ==========================================
    // Dashboard Sections Management
    // ==========================================

    private void renderSections() {
        containerSettingsSections.removeAllViews();
        List<DashboardSection> sections = DashboardConfigManager.load(this);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (DashboardSection section : sections) {
            View sectionView = inflater.inflate(R.layout.item_settings_section, containerSettingsSections, false);
            TextView textSectionTitle = sectionView.findViewById(R.id.text_section_title);
            MaterialButton btnEditSection = sectionView.findViewById(R.id.btn_edit_section);
            MaterialButton btnDeleteSection = sectionView.findViewById(R.id.btn_delete_section);
            MaterialButton btnAddItem = sectionView.findViewById(R.id.btn_add_item_to_section);
            LinearLayout containerItems = sectionView.findViewById(R.id.container_section_items);

            textSectionTitle.setText(section.title);
            btnEditSection.setOnClickListener(v -> showSectionDialog(section));
            btnDeleteSection.setOnClickListener(v -> {
                List<DashboardSection> current = DashboardConfigManager.load(this);
                current.removeIf(s -> s.id.equals(section.id));
                DashboardConfigManager.save(this, current);
                renderSections();
            });

            btnAddItem.setOnClickListener(v -> showItemDialog(section, null));

            for (DashboardItem item : section.items) {
                View itemView = inflater.inflate(R.layout.item_settings_dashboard_item, containerItems, false);
                ImageView imgItemIcon = itemView.findViewById(R.id.img_item_icon);
                TextView textItemTitle = itemView.findViewById(R.id.text_item_title);
                TextView textItemType = itemView.findViewById(R.id.text_item_type);
                MaterialButton btnEditItem = itemView.findViewById(R.id.btn_edit_item);
                MaterialButton btnDeleteItem = itemView.findViewById(R.id.btn_delete_item);

                imgItemIcon.setImageResource(IconHelper.getDrawableRes(item.icon));
                textItemTitle.setText(item.title);
                textItemType.setText(item.type);

                btnEditItem.setOnClickListener(v -> showItemDialog(section, item));
                btnDeleteItem.setOnClickListener(v -> {
                    List<DashboardSection> current = DashboardConfigManager.load(this);
                    for (DashboardSection s : current) {
                        if (s.id.equals(section.id)) {
                            s.items.removeIf(i -> i.id.equals(item.id));
                            break;
                        }
                    }
                    DashboardConfigManager.save(this, current);
                    renderSections();
                });

                containerItems.addView(itemView);
            }

            containerSettingsSections.addView(sectionView);
        }
    }

    private void showSectionDialog(@Nullable DashboardSection existingSection) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dashboard_item, null);
        // We can reuse a simple text layout or create programmatically
        final TextInputLayout textInputLayout = new TextInputLayout(this, null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        textInputLayout.setHint(getString(R.string.hint_section_title));
        final TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        editText.setSingleLine(true);
        if (existingSection != null) {
            editText.setText(existingSection.title);
        }
        textInputLayout.addView(editText);

        FrameLayout container = new FrameLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(textInputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(existingSection != null ? R.string.dialog_title_edit_section : R.string.dialog_title_add_section)
                .setView(container)
                .setPositiveButton(R.string.btn_save, (d, w) -> {
                    String title = editText.getText() != null ? editText.getText().toString().trim() : "";
                    if (!title.isEmpty()) {
                        List<DashboardSection> current = DashboardConfigManager.load(this);
                        if (existingSection != null) {
                            for (DashboardSection s : current) {
                                if (s.id.equals(existingSection.id)) {
                                    s.title = title;
                                    break;
                                }
                            }
                        } else {
                            current.add(new DashboardSection(UUID.randomUUID().toString(), title, null));
                        }
                        DashboardConfigManager.save(this, current);
                        renderSections();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showItemDialog(DashboardSection section, @Nullable DashboardItem existingItem) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dashboard_item, null);
        TextInputLayout layoutTitle = dialogView.findViewById(R.id.layout_item_title);
        TextInputEditText editTitle = dialogView.findViewById(R.id.edit_item_title);

        RadioGroup groupType = dialogView.findViewById(R.id.group_item_type);
        TextInputEditText editIcon = dialogView.findViewById(R.id.edit_item_icon);

        TextInputLayout layoutOnCmd = dialogView.findViewById(R.id.layout_on_command);
        TextInputEditText editOnCmd = dialogView.findViewById(R.id.edit_on_command);

        TextInputLayout layoutOffCmd = dialogView.findViewById(R.id.layout_off_command);
        TextInputEditText editOffCmd = dialogView.findViewById(R.id.edit_off_command);

        TextInputLayout layoutStateCheckCmd = dialogView.findViewById(R.id.layout_state_check_command);
        TextInputEditText editStateCheckCmd = dialogView.findViewById(R.id.edit_state_check_command);

        TextInputLayout layoutSummaryCmd = dialogView.findViewById(R.id.layout_summary_command);
        TextInputEditText editSummaryCmd = dialogView.findViewById(R.id.edit_summary_command);

        TextInputLayout layoutDisableConditionCmd = dialogView.findViewById(R.id.layout_disable_condition_command);
        TextInputEditText editDisableConditionCmd = dialogView.findViewById(R.id.edit_disable_condition_command);

        // Toggle visibility of switch-specific fields
        groupType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isSwitch = (checkedId == R.id.radio_switch);
            layoutOffCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
            layoutStateCheckCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
        });

        if (existingItem != null) {
            editTitle.setText(existingItem.title);
            boolean isSwitch = DashboardItem.TYPE_SWITCH.equalsIgnoreCase(existingItem.type);
            groupType.check(isSwitch ? R.id.radio_switch : R.id.radio_button);
            layoutOffCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
            layoutStateCheckCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);

            editIcon.setText(existingItem.icon);
            editOnCmd.setText(existingItem.onCommand);
            editOffCmd.setText(existingItem.offCommand);
            editStateCheckCmd.setText(existingItem.stateCheckCommand);
            editSummaryCmd.setText(existingItem.summaryCommand);
            editDisableConditionCmd.setText(existingItem.disableConditionCommand);
        } else {
            groupType.check(R.id.radio_button);
            layoutOffCmd.setVisibility(View.GONE);
            layoutStateCheckCmd.setVisibility(View.GONE);
            editIcon.setText("terminal");
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existingItem != null ? R.string.dialog_title_edit_item : R.string.dialog_title_add_item)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = editTitle.getText() != null ? editTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                layoutTitle.setError(getString(R.string.error_name_empty));
                return;
            } else {
                layoutTitle.setError(null);
            }

            boolean isSwitch = (groupType.getCheckedRadioButtonId() == R.id.radio_switch);
            String type = isSwitch ? DashboardItem.TYPE_SWITCH : DashboardItem.TYPE_BUTTON;
            String icon = editIcon.getText() != null ? editIcon.getText().toString().trim().toLowerCase() : "shield";
            if (icon.isEmpty()) icon = "shield";

            String onCmd = editOnCmd.getText() != null ? editOnCmd.getText().toString().trim() : "";
            String offCmd = editOffCmd.getText() != null ? editOffCmd.getText().toString().trim() : "";
            String stateCheckCmd = editStateCheckCmd.getText() != null ? editStateCheckCmd.getText().toString().trim() : "";
            String summaryCmd = editSummaryCmd.getText() != null ? editSummaryCmd.getText().toString().trim() : "";
            String disableConditionCmd = editDisableConditionCmd.getText() != null ? editDisableConditionCmd.getText().toString().trim() : "";

            List<DashboardSection> current = DashboardConfigManager.load(this);
            for (DashboardSection s : current) {
                if (s.id.equals(section.id)) {
                    if (existingItem != null) {
                        for (DashboardItem i : s.items) {
                            if (i.id.equals(existingItem.id)) {
                                i.title = title;
                                i.type = type;
                                i.icon = icon;
                                i.onCommand = onCmd;
                                i.offCommand = offCmd;
                                i.stateCheckCommand = stateCheckCmd;
                                i.summaryCommand = summaryCmd;
                                i.disableConditionCommand = disableConditionCmd;
                                break;
                            }
                        }
                    } else {
                        s.items.add(new DashboardItem(
                                UUID.randomUUID().toString(),
                                title,
                                type,
                                icon,
                                onCmd,
                                offCmd,
                                stateCheckCmd,
                                summaryCmd,
                                disableConditionCmd,
                                false
                        ));
                    }
                    break;
                }
            }
            DashboardConfigManager.save(this, current);
            renderSections();
            dialog.dismiss();
        });
    }
}
