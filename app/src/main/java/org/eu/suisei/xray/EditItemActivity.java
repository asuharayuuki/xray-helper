package org.eu.suisei.xray;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
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
import java.util.List;
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
    private RadioGroup groupType;
    private TextInputEditText editIcon;

    private TextInputLayout layoutOnCmd;
    private TextInputEditText editOnCmd;
    private TextInputLayout layoutOffCmd;
    private TextInputEditText editOffCmd;
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
        groupType = findViewById(R.id.group_item_type);
        editIcon = findViewById(R.id.edit_item_icon);

        layoutOnCmd = findViewById(R.id.layout_on_cmd);
        editOnCmd = findViewById(R.id.edit_on_cmd);
        layoutOffCmd = findViewById(R.id.layout_off_cmd);
        editOffCmd = findViewById(R.id.edit_off_cmd);
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
            collapsingToolbar.setTitle("Edit item");
            editTitle.setText(editingItem.title);
            editSectionName.setText(parentSection != null ? parentSection.title : "Actions");

            boolean isSwitch = DashboardItem.TYPE_SWITCH.equalsIgnoreCase(editingItem.type);
            groupType.check(isSwitch ? R.id.radio_switch : R.id.radio_button);
            layoutOffCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
            layoutStateCheckCmd.setVisibility(isSwitch ? View.VISIBLE : View.GONE);

            editIcon.setText(editingItem.icon != null ? editingItem.icon : "shield");
            editOnCmd.setText(editingItem.onCommand);
            editOffCmd.setText(editingItem.offCommand);
            editStateCheckCmd.setText(editingItem.stateCheckCommand);
            editSummaryCmd.setText(editingItem.summaryCommand);
            editDisableConditionCmd.setText(editingItem.disableConditionCommand);

            if (!editingItem.isCore) {
                btnDeleteItem.setVisibility(View.VISIBLE);
            } else {
                btnDeleteItem.setVisibility(View.GONE);
            }
        } else {
            collapsingToolbar.setTitle("New item");
            editSectionName.setText(defaultSection);
            groupType.check(R.id.radio_button);
            layoutOffCmd.setVisibility(View.GONE);
            layoutStateCheckCmd.setVisibility(View.GONE);
            editIcon.setText("terminal");
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
            targetSectionTitle = "Actions";
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

        if (editingItem != null) {
            // Remove from old section if section changed
            if (parentSection != null && !parentSection.title.equalsIgnoreCase(targetSectionTitle)) {
                for (DashboardSection s : sections) {
                    if (s.id.equals(parentSection.id)) {
                        s.items.removeIf(i -> i.id.equals(editingItem.id));
                        break;
                    }
                }
                // Add to new section
                editingItem.title = title;
                editingItem.type = type;
                editingItem.icon = icon;
                editingItem.onCommand = onCmd;
                editingItem.offCommand = offCmd;
                editingItem.stateCheckCommand = stateCheckCmd;
                editingItem.summaryCommand = summaryCmd;
                editingItem.disableConditionCommand = disableConditionCmd;
                sectionToPlace.items.add(editingItem);
            } else {
                // Update in place
                for (DashboardSection s : sections) {
                    for (DashboardItem i : s.items) {
                        if (i.id.equals(editingItem.id)) {
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
                }
            }
        } else {
            // Create new item
            DashboardItem newItem = new DashboardItem(
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
