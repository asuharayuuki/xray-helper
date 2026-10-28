package org.eu.suisei.xray;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EditItemActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_DEFAULT_SECTION = "extra_default_section";

    private String targetItemId = null;
    private DashboardItem editingItem = null;
    private DashboardSection parentSection = null;

    private CollapsingToolbarLayout collapsingToolbar;
    private MaterialToolbar toolbar;
    private TextInputLayout layoutTitle;
    private TextInputEditText editTitle;
    private TextInputEditText editSectionName;
    private View layoutControlTypeWrapper;
    private RadioGroup groupType;

    // Icon Picker Views
    private View rowSelectIcon;
    private ImageView imgSelectedIcon;
    private TextView textSelectedIconName;
    private LinearLayout containerQuickIcons;
    private String selectedIcon = "shield";
    private final Map<String, View> quickIconViews = new HashMap<>();

    // Command Views
    private TextInputLayout layoutOnCmd;
    private TextInputEditText editOnCmd;
    private TextInputLayout layoutOffCmd;
    private TextInputEditText editOffCmd;
    private TextInputLayout layoutProcessName;
    private TextInputEditText editProcessName;
    private TextInputLayout layoutStateCheckCmd;
    private TextInputEditText editStateCheckCmd;
    private TextInputLayout layoutSummaryCmd;
    private TextInputEditText editSummaryCmd;
    private TextInputLayout layoutDisableConditionCmd;
    private TextInputEditText editDisableConditionCmd;

    private MaterialButton btnDeleteItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_item);

        setupWindowInsets();
        initViews();
        setupIconPicker();
        loadData();
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

            int bottomPadding = insets.bottom + (int) (32 * getResources().getDisplayMetrics().density);
            scrollContent.setPadding(insets.left + (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (8 * getResources().getDisplayMetrics().density),
                    insets.right + (int) (16 * getResources().getDisplayMetrics().density),
                    bottomPadding);
            return windowInsets;
        });
    }

    private void initViews() {
        collapsingToolbar = findViewById(R.id.collapsing_toolbar);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_edit_item);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveItem();
                return true;
            }
            return false;
        });

        layoutTitle = findViewById(R.id.layout_item_title);
        editTitle = findViewById(R.id.edit_item_title);
        editSectionName = findViewById(R.id.edit_section_name);
        layoutControlTypeWrapper = findViewById(R.id.layout_control_type_wrapper);
        groupType = findViewById(R.id.group_item_type);

        rowSelectIcon = findViewById(R.id.row_select_icon);
        imgSelectedIcon = findViewById(R.id.img_selected_icon);
        textSelectedIconName = findViewById(R.id.text_selected_icon_name);
        containerQuickIcons = findViewById(R.id.container_quick_icons);

        layoutOnCmd = findViewById(R.id.layout_on_cmd);
        editOnCmd = findViewById(R.id.edit_on_cmd);
        layoutOffCmd = findViewById(R.id.layout_off_cmd);
        editOffCmd = findViewById(R.id.edit_off_cmd);
        layoutProcessName = findViewById(R.id.layout_process_name);
        editProcessName = findViewById(R.id.edit_process_name);
        layoutStateCheckCmd = findViewById(R.id.layout_state_check_cmd);
        editStateCheckCmd = findViewById(R.id.edit_state_check_cmd);
        layoutSummaryCmd = findViewById(R.id.layout_summary_cmd);
        editSummaryCmd = findViewById(R.id.edit_summary_cmd);
        layoutDisableConditionCmd = findViewById(R.id.layout_disable_condition_cmd);
        editDisableConditionCmd = findViewById(R.id.edit_disable_condition_cmd);

        btnDeleteItem = findViewById(R.id.btn_delete_item);

        groupType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isSwitch = (checkedId == R.id.radio_switch);
            layoutOffCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
            layoutStateCheckCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
        });

        btnDeleteItem.setOnClickListener(v -> confirmDeleteItem());
    }

    private void setupIconPicker() {
        containerQuickIcons.removeAllViews();
        quickIconViews.clear();
        int size = (int) (46 * getResources().getDisplayMetrics().density);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);

        for (String iconName : IconHelper.AVAILABLE_ICONS) {
            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            frame.setLayoutParams(lp);
            frame.setBackgroundResource(R.drawable.bg_icon_avatar);

            ImageView iconView = new ImageView(this);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams((int) (22 * getResources().getDisplayMetrics().density),
                    (int) (22 * getResources().getDisplayMetrics().density));
            iconLp.gravity = Gravity.CENTER;
            iconView.setLayoutParams(iconLp);
            iconView.setImageResource(IconHelper.getDrawableRes(iconName));
            frame.addView(iconView);

            frame.setOnClickListener(v -> setSelectedIcon(iconName));
            containerQuickIcons.addView(frame);
            quickIconViews.put(iconName, frame);
        }

        rowSelectIcon.setOnClickListener(v -> showIconPickerDialog());
    }

    private void setSelectedIcon(String iconName) {
        if (iconName == null || iconName.trim().isEmpty()) iconName = "shield";
        this.selectedIcon = iconName.toLowerCase();

        imgSelectedIcon.setImageResource(IconHelper.getDrawableRes(selectedIcon));
        textSelectedIconName.setText(formatIconName(selectedIcon));

        // Update highlight state in quick bar
        for (Map.Entry<String, View> entry : quickIconViews.entrySet()) {
            boolean isSelected = entry.getKey().equalsIgnoreCase(selectedIcon);
            entry.getValue().setBackgroundResource(isSelected ? R.drawable.bg_quick_icon_selected : R.drawable.bg_icon_avatar);
        }
    }

    private String formatIconName(String name) {
        if (name == null || name.isEmpty()) return "Shield";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }

    private void showIconPickerDialog() {
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(pad, pad, pad, pad);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Select icon")
                .setView(grid)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        int itemWidth = (int) (72 * getResources().getDisplayMetrics().density);
        int itemHeight = (int) (76 * getResources().getDisplayMetrics().density);

        for (String iconName : IconHelper.AVAILABLE_ICONS) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER);
            itemLayout.setLayoutParams(new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ));
            itemLayout.setMinimumWidth(itemWidth);
            itemLayout.setMinimumHeight(itemHeight);
            itemLayout.setBackgroundResource(R.drawable.bg_picker_row);
            itemLayout.setClickable(true);
            itemLayout.setFocusable(true);

            FrameLayout iconContainer = new FrameLayout(this);
            int avatarSize = (int) (40 * getResources().getDisplayMetrics().density);
            iconContainer.setLayoutParams(new LinearLayout.LayoutParams(avatarSize, avatarSize));
            boolean isSelected = iconName.equalsIgnoreCase(selectedIcon);
            iconContainer.setBackgroundResource(isSelected ? R.drawable.bg_quick_icon_selected : R.drawable.bg_icon_avatar);

            ImageView iconView = new ImageView(this);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams((int) (22 * getResources().getDisplayMetrics().density),
                    (int) (22 * getResources().getDisplayMetrics().density));
            iconLp.gravity = Gravity.CENTER;
            iconView.setLayoutParams(iconLp);
            iconView.setImageResource(IconHelper.getDrawableRes(iconName));
            iconContainer.addView(iconView);

            TextView nameView = new TextView(this);
            nameView.setText(formatIconName(iconName));
            nameView.setTextSize(12f);
            nameView.setGravity(Gravity.CENTER);
            nameView.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);

            itemLayout.addView(iconContainer);
            itemLayout.addView(nameView);

            itemLayout.setOnClickListener(v -> {
                setSelectedIcon(iconName);
                dialog.dismiss();
            });

            grid.addView(itemLayout);
        }

        dialog.show();
    }

    private void loadData() {
        targetItemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        String defaultSection = getIntent().getStringExtra(EXTRA_DEFAULT_SECTION);
        if (defaultSection == null || defaultSection.trim().isEmpty()) {
            defaultSection = "Actions";
        }

        List<DashboardSection> sections = DashboardConfigManager.load(this);
        if (targetItemId != null) {
            for (DashboardSection sec : sections) {
                for (DashboardItem item : sec.items) {
                    if (item.id.equals(targetItemId)) {
                        editingItem = item;
                        parentSection = sec;
                        break;
                    }
                }
                if (editingItem != null) break;
            }
        }

        if (editingItem != null) {
            editTitle.setText(editingItem.title);
            editSectionName.setText(parentSection != null ? parentSection.title : (editingItem.isCore ? "Proxy" : "Actions"));
            setSelectedIcon(editingItem.icon != null ? editingItem.icon : "shield");

            if (editingItem.isCore) {
                collapsingToolbar.setTitle("Edit Core");
                // Core is locked to Switch type; hide radio group
                layoutControlTypeWrapper.setVisibility(View.GONE);

                // Core uses start/stop commands and process name from AppConfig
                layoutOnCmd.setHint("Start command");
                editOnCmd.setText(AppConfig.getStartCommand(this));

                layoutOffCmd.setVisibility(View.VISIBLE);
                layoutOffCmd.setHint("Stop command");
                editOffCmd.setText(AppConfig.getStopCommand(this));

                layoutProcessName.setVisibility(View.VISIBLE);
                editProcessName.setText(AppConfig.getProcessName(this));

                // Core has dedicated 4-state summary and logic
                layoutStateCheckCmd.setVisibility(View.GONE);
                layoutSummaryCmd.setVisibility(View.GONE);
                layoutDisableConditionCmd.setVisibility(View.GONE);

                btnDeleteItem.setVisibility(View.GONE);
            } else {
                collapsingToolbar.setTitle("Edit item");
                layoutControlTypeWrapper.setVisibility(View.VISIBLE);

                boolean isSwitch = DashboardItem.TYPE_SWITCH.equalsIgnoreCase(editingItem.type);
                groupType.check(isSwitch ? R.id.radio_switch : R.id.radio_button);
                layoutOffCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
                layoutOffCmd.setHint("Off command");
                layoutStateCheckCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);

                layoutProcessName.setVisibility(View.GONE);
                layoutSummaryCmd.setVisibility(View.VISIBLE);
                layoutDisableConditionCmd.setVisibility(View.VISIBLE);

                editOnCmd.setText(editingItem.onCommand);
                editOffCmd.setText(editingItem.offCommand);
                editStateCheckCmd.setText(editingItem.stateCheckCommand);
                editSummaryCmd.setText(editingItem.summaryCommand);
                editDisableConditionCmd.setText(editingItem.disableConditionCommand);

                btnDeleteItem.setVisibility(View.VISIBLE);
            }
        } else {
            collapsingToolbar.setTitle("New item");
            editSectionName.setText(defaultSection);
            layoutControlTypeWrapper.setVisibility(View.VISIBLE);
            groupType.check(R.id.radio_button);
            layoutOffCmd.setVisibility(View.GONE);
            layoutProcessName.setVisibility(View.GONE);
            layoutStateCheckCmd.setVisibility(View.GONE);
            layoutSummaryCmd.setVisibility(View.VISIBLE);
            layoutDisableConditionCmd.setVisibility(View.VISIBLE);
            setSelectedIcon("terminal");
            btnDeleteItem.setVisibility(View.GONE);
        }
    }

    private void saveItem() {
        String title = editTitle.getText() != null ? editTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            layoutTitle.setError(getString(R.string.error_name_empty));
            return;
        } else {
            layoutTitle.setError(null);
        }

        String targetSectionTitle = editSectionName.getText() != null ? editSectionName.getText().toString().trim() : "";
        if (targetSectionTitle.isEmpty()) {
            targetSectionTitle = (editingItem != null && editingItem.isCore) ? "Proxy" : "Actions";
        }

        String onCmd = editOnCmd.getText() != null ? editOnCmd.getText().toString().trim() : "";
        String offCmd = editOffCmd.getText() != null ? editOffCmd.getText().toString().trim() : "";

        List<DashboardSection> sections = DashboardConfigManager.load(this);

        // Find or create target section
        DashboardSection sectionToPlace = null;
        for (DashboardSection s : sections) {
            if (s.title.equalsIgnoreCase(targetSectionTitle)) {
                sectionToPlace = s;
                break;
            }
        }
        if (sectionToPlace == null) {
            sectionToPlace = new DashboardSection(UUID.randomUUID().toString(), targetSectionTitle, new ArrayList<>());
            sections.add(sectionToPlace);
        }

        if (editingItem != null && editingItem.isCore) {
            String procName = editProcessName.getText() != null ? editProcessName.getText().toString().trim() : "xray";
            if (procName.isEmpty()) procName = "xray";

            // Save core commands directly to AppConfig
            if (!onCmd.isEmpty()) AppConfig.setStartCommand(this, onCmd);
            if (!offCmd.isEmpty()) AppConfig.setStopCommand(this, offCmd);
            AppConfig.setProcessName(this, procName);

            // Update item in DashboardConfig
            editingItem.title = title;
            editingItem.icon = selectedIcon;
            editingItem.onCommand = onCmd;
            editingItem.offCommand = offCmd;

            if (parentSection != null && !parentSection.title.equalsIgnoreCase(targetSectionTitle)) {
                for (DashboardSection s : sections) {
                    if (s.id.equals(parentSection.id)) {
                        s.items.removeIf(i -> i.id.equals(editingItem.id));
                        break;
                    }
                }
                sectionToPlace.items.add(editingItem);
            } else {
                for (DashboardSection s : sections) {
                    for (DashboardItem i : s.items) {
                        if (i.id.equals(editingItem.id)) {
                            i.title = title;
                            i.icon = selectedIcon;
                            i.onCommand = onCmd;
                            i.offCommand = offCmd;
                            break;
                        }
                    }
                }
            }

            DashboardConfigManager.save(this, sections);
            Toast.makeText(this, "Core settings saved", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Non-core items
        boolean isSwitch = (groupType.getCheckedRadioButtonId() == R.id.radio_switch);
        String type = isSwitch ? DashboardItem.TYPE_SWITCH : DashboardItem.TYPE_BUTTON;
        String stateCheckCmd = editStateCheckCmd.getText() != null ? editStateCheckCmd.getText().toString().trim() : "";
        String summaryCmd = editSummaryCmd.getText() != null ? editSummaryCmd.getText().toString().trim() : "";
        String disableConditionCmd = editDisableConditionCmd.getText() != null ? editDisableConditionCmd.getText().toString().trim() : "";

        if (editingItem != null) {
            // Remove from old section if section changed
            if (parentSection != null && !parentSection.title.equalsIgnoreCase(targetSectionTitle)) {
                for (DashboardSection s : sections) {
                    if (s.id.equals(parentSection.id)) {
                        s.items.removeIf(i -> i.id.equals(editingItem.id));
                        break;
                    }
                }
                editingItem.title = title;
                editingItem.type = type;
                editingItem.icon = selectedIcon;
                editingItem.onCommand = onCmd;
                editingItem.offCommand = offCmd;
                editingItem.stateCheckCommand = stateCheckCmd;
                editingItem.summaryCommand = summaryCmd;
                editingItem.disableConditionCommand = disableConditionCmd;
                sectionToPlace.items.add(editingItem);
            } else {
                for (DashboardSection s : sections) {
                    for (DashboardItem i : s.items) {
                        if (i.id.equals(editingItem.id)) {
                            i.title = title;
                            i.type = type;
                            i.icon = selectedIcon;
                            i.onCommand = onCmd;
                            i.offCommand = offCmd;
                            i.stateCheckCommand = stateCheckCmd;
                            i.summaryCommand = summaryCmd;
                            i.disableConditionCommand = disableConditionCmd;
                            break;
                        }
                    }
                }
            }
        } else {
            DashboardItem newItem = new DashboardItem(
                    UUID.randomUUID().toString(),
                    title,
                    type,
                    selectedIcon,
                    onCmd,
                    offCmd,
                    stateCheckCmd,
                    summaryCmd,
                    disableConditionCmd,
                    false
            );
            sectionToPlace.items.add(newItem);
        }

        DashboardConfigManager.save(this, sections);
        Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDeleteItem() {
        if (editingItem == null || editingItem.isCore) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete item")
                .setMessage("Are you sure you want to delete '" + editingItem.title + "'?")
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    List<DashboardSection> sections = DashboardConfigManager.load(this);
                    for (DashboardSection s : sections) {
                        s.items.removeIf(i -> i.id.equals(editingItem.id));
                    }
                    DashboardConfigManager.save(this, sections);
                    Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
