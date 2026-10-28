package org.eu.suisei.xray;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout containerDashboardItemsList;
    private View rowResetDefaults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupWindowInsets();
        setupToolbar();
        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDashboardItems();
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
        containerDashboardItemsList = findViewById(R.id.container_dashboard_items_list);
        rowResetDefaults = findViewById(R.id.row_reset_defaults);
        rowResetDefaults.setOnClickListener(v -> resetAllToDefaults());
    }

    private void renderDashboardItems() {
        containerDashboardItemsList.removeAllViews();
        List<DashboardSection> sections = DashboardConfigManager.load(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        int dividerInset = (int) (76 * getResources().getDisplayMetrics().density);

        for (DashboardSection section : sections) {
            for (DashboardItem item : section.items) {
                View row = inflater.inflate(R.layout.item_settings_preference_row, containerDashboardItemsList, false);

                ImageView imgIcon = row.findViewById(R.id.img_icon);
                TextView textTitle = row.findViewById(R.id.text_title);
                TextView textSubtitle = row.findViewById(R.id.text_subtitle);

                imgIcon.setImageResource(IconHelper.getDrawableRes(item.icon));
                textTitle.setText(item.title);

                String typeStr = item.isCore ? "Core switch" : (DashboardItem.TYPE_SWITCH.equalsIgnoreCase(item.type) ? "Switch" : "Button");
                textSubtitle.setText(section.title + " • " + typeStr);

                row.setOnClickListener(v -> {
                    Intent intent = new Intent(SettingsActivity.this, EditItemActivity.class);
                    intent.putExtra(EditItemActivity.EXTRA_ITEM_ID, item.id);
                    intent.putExtra(EditItemActivity.EXTRA_DEFAULT_SECTION, section.title);
                    startActivity(intent);
                });

                containerDashboardItemsList.addView(row);

                // Add divider
                MaterialDivider divider = new MaterialDivider(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                divider.setLayoutParams(lp);
                divider.setDividerInsetStart(dividerInset);
                divider.setDividerInsetEnd((int) (20 * getResources().getDisplayMetrics().density));
                containerDashboardItemsList.addView(divider);
            }
        }

        // Add "Add new item" row at the bottom
        View addRow = inflater.inflate(R.layout.item_settings_preference_row, containerDashboardItemsList, false);
        ImageView addIcon = addRow.findViewById(R.id.img_icon);
        TextView addTitle = addRow.findViewById(R.id.text_title);
        TextView addSubtitle = addRow.findViewById(R.id.text_subtitle);

        addIcon.setImageResource(R.drawable.ic_add);
        addTitle.setText("Add new item");
        addSubtitle.setText("Create custom switch or action button");

        addRow.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, EditItemActivity.class);
            startActivity(intent);
        });

        containerDashboardItemsList.addView(addRow);
    }

    private void resetAllToDefaults() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.btn_reset_defaults)
                .setMessage("Reset all Xray commands and dashboard layout to defaults?")
                .setPositiveButton("Reset", (d, w) -> {
                    AppConfig.resetDefaults(this);
                    List<DashboardSection> defaults = DashboardConfigManager.createDefaults();
                    DashboardConfigManager.save(this, defaults);
                    renderDashboardItems();
                    Toast.makeText(this, R.string.msg_defaults_restored, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
