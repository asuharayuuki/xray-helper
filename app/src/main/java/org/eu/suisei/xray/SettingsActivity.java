package org.eu.suisei.xray;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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
    private MaterialButton btnResetCommands;

    private MaterialButton btnAddScript;
    private TextView textEmptyScripts;
    private LinearLayout containerCustomScripts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupWindowInsets();
        setupToolbar();
        initViews();
        loadCommandConfig();
        renderCustomScripts();
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
            int bottomPadding = insets.bottom + (int) (24 * getResources().getDisplayMetrics().density);

            scrollContent.setPadding(
                    insets.left + horizontalPadding,
                    topPadding,
                    insets.right + horizontalPadding,
                    bottomPadding
            );
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
        btnResetCommands = findViewById(R.id.btn_reset_commands);

        btnAddScript = findViewById(R.id.btn_add_script);
        textEmptyScripts = findViewById(R.id.text_empty_scripts);
        containerCustomScripts = findViewById(R.id.container_settings_custom_scripts);

        btnSaveCommands.setOnClickListener(v -> saveCommandConfig());
        btnResetCommands.setOnClickListener(v -> resetCommandConfig());
        btnAddScript.setOnClickListener(v -> showScriptDialog(null));
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
            Toast.makeText(this, "Commands and parameters cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        AppConfig.setStartCommand(this, startCmd);
        AppConfig.setStopCommand(this, stopCmd);
        AppConfig.setHttpProxy(this, proxy);
        AppConfig.setProcessName(this, procName);

        Toast.makeText(this, R.string.msg_commands_saved, Toast.LENGTH_SHORT).show();
    }

    private void resetCommandConfig() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.btn_reset_defaults)
                .setMessage("Reset all Xray commands to default settings?")
                .setPositiveButton("Reset", (d, w) -> {
                    AppConfig.resetDefaults(this);
                    loadCommandConfig();
                    Toast.makeText(this, R.string.msg_defaults_restored, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ==========================================
    // Custom Scripts Management
    // ==========================================

    private void renderCustomScripts() {
        containerCustomScripts.removeAllViews();
        List<CustomAction> list = CustomActionManager.load(this);

        if (list.isEmpty()) {
            textEmptyScripts.setVisibility(View.VISIBLE);
        } else {
            textEmptyScripts.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (CustomAction action : list) {
                View item = inflater.inflate(R.layout.item_settings_custom_action, containerCustomScripts, false);
                TextView textName = item.findViewById(R.id.text_script_name);
                TextView textCommand = item.findViewById(R.id.text_script_command);
                TextView textCondition = item.findViewById(R.id.text_script_condition);
                MaterialButton btnEdit = item.findViewById(R.id.btn_edit_script);
                MaterialButton btnDelete = item.findViewById(R.id.btn_delete_script);

                textName.setText(action.name);
                textCommand.setText(action.command);

                if (action.conditionCommand != null && !action.conditionCommand.trim().isEmpty()) {
                    textCondition.setText("Disabled if: " + action.conditionCommand);
                    textCondition.setVisibility(View.VISIBLE);
                } else {
                    textCondition.setVisibility(View.GONE);
                }

                item.setOnClickListener(v -> showScriptDialog(action));
                btnEdit.setOnClickListener(v -> showScriptDialog(action));
                btnDelete.setOnClickListener(v -> {
                    List<CustomAction> current = CustomActionManager.load(SettingsActivity.this);
                    current.removeIf(a -> a.id.equals(action.id));
                    CustomActionManager.save(SettingsActivity.this, current);
                    renderCustomScripts();
                });

                containerCustomScripts.addView(item);
            }
        }
    }

    private void showScriptDialog(@Nullable CustomAction existingAction) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_action, null);
        TextInputLayout layoutName = dialogView.findViewById(R.id.layout_action_name);
        TextInputLayout layoutCommand = dialogView.findViewById(R.id.layout_action_command);
        TextInputEditText editName = dialogView.findViewById(R.id.edit_action_name);
        TextInputEditText editCommand = dialogView.findViewById(R.id.edit_action_command);
        TextInputEditText editCondition = dialogView.findViewById(R.id.edit_action_condition);

        boolean isEditing = (existingAction != null);
        if (isEditing) {
            editName.setText(existingAction.name);
            editCommand.setText(existingAction.command);
            editCondition.setText(existingAction.conditionCommand);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(isEditing ? R.string.dialog_title_edit : R.string.dialog_title_add)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, (d, w) -> d.dismiss());

        if (isEditing) {
            builder.setNeutralButton(R.string.btn_delete, (d, w) -> {
                List<CustomAction> list = CustomActionManager.load(SettingsActivity.this);
                list.removeIf(a -> a.id.equals(existingAction.id));
                CustomActionManager.save(SettingsActivity.this, list);
                renderCustomScripts();
            });
        }

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = editName.getText() != null ? editName.getText().toString().trim() : "";
            String command = editCommand.getText() != null ? editCommand.getText().toString().trim() : "";
            String condition = editCondition.getText() != null ? editCondition.getText().toString().trim() : "";

            boolean valid = true;
            if (name.isEmpty()) {
                layoutName.setError(getString(R.string.error_name_empty));
                valid = false;
            } else {
                layoutName.setError(null);
            }

            if (command.isEmpty()) {
                layoutCommand.setError(getString(R.string.error_command_empty));
                valid = false;
            } else {
                layoutCommand.setError(null);
            }

            if (valid) {
                List<CustomAction> list = CustomActionManager.load(SettingsActivity.this);
                if (isEditing) {
                    for (CustomAction a : list) {
                        if (a.id.equals(existingAction.id)) {
                            a.name = name;
                            a.command = command;
                            a.conditionCommand = condition;
                            break;
                        }
                    }
                } else {
                    list.add(new CustomAction(UUID.randomUUID().toString(), name, command, condition));
                }
                CustomActionManager.save(SettingsActivity.this, list);
                renderCustomScripts();
                dialog.dismiss();
            }
        });
    }
}
