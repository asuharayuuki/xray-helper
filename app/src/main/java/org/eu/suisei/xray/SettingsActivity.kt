package org.eu.suisei.xray

import android.content.ComponentName
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.suisei.xray.ui.MiuixAppTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity : ComponentActivity() {

    private val sectionsState = mutableStateOf<List<DashboardSection>>(emptyList())
    private val transferBusy = mutableStateOf(false)
    private val importState by lazy { ViewModelProvider(this)[ImportState::class.java] }
    private val chooseConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readConfiguration(uri)
    }

    private fun readConfiguration(uri: Uri) {
        lifecycleScope.launch {
            transferBusy.value = true
            try {
                importState.pending.value = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { ConfigCodec.decode(ConfigCodec.read(it)) }
                        ?: error("Unable to open configuration")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_import_invalid, Toast.LENGTH_LONG).show()
            } finally {
                transferBusy.value = false
            }
        }
    }

    private fun importConfiguration() {
        val pending = importState.pending.value ?: return
        lifecycleScope.launch {
            transferBusy.value = true
            try {
                withContext(Dispatchers.IO) { AppConfig.importSnapshot(this@SettingsActivity, pending) }
                importState.pending.value = null
                sectionsState.value = DashboardConfigManager.load(this@SettingsActivity)
                XrayTileService.requestRefresh(this@SettingsActivity)
                try {
                    TileService.requestListeningState(this@SettingsActivity, ComponentName(this@SettingsActivity, RecordTileService::class.java))
                } catch (ignored: Exception) {}
                Toast.makeText(this@SettingsActivity, R.string.msg_import_success, Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_import_failed, Toast.LENGTH_LONG).show()
            } finally {
                transferBusy.value = false
            }
        }
    }

    private fun exportConfiguration() {
        lifecycleScope.launch {
            transferBusy.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val json = ConfigCodec.encode(AppConfig.snapshot(this@SettingsActivity))
                    val directory = File(cacheDir, "configuration_exports").apply { mkdirs() }
                    // Keep recent shares available; discard old exports after seven days.
                    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7 * 86400000L }
                        ?.forEach { it.delete() }
                    File.createTempFile("Xray-config-", ".json", directory).apply { writeText(json, Charsets.UTF_8) }
                }
                val uri = FileProvider.getUriForFile(this@SettingsActivity, "$packageName.files", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Xray configuration", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, getString(R.string.action_export_config)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.msg_export_failed, Toast.LENGTH_LONG).show()
            } finally {
                transferBusy.value = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MiuixAppTheme {
                SettingsScreen(
                    sections = sectionsState.value,
                    transferBusy = transferBusy.value,
                    hasPendingImport = importState.pending.value != null,
                    onImport = {
                        try {
                            chooseConfig.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            Toast.makeText(this@SettingsActivity, R.string.msg_picker_unavailable, Toast.LENGTH_LONG).show()
                        }
                    },
                    onExport = { exportConfiguration() },
                    onConfirmImport = { importConfiguration() },
                    onCancelImport = { importState.pending.value = null },
                    onBack = { finish() },
                    onItemClick = { item, sectionTitle ->
                        val intent = Intent(this@SettingsActivity, EditItemActivity::class.java).apply {
                            putExtra(EditItemActivity.EXTRA_ITEM_ID, item.id)
                            putExtra(EditItemActivity.EXTRA_DEFAULT_SECTION, sectionTitle)
                        }
                        startActivity(intent)
                    },
                    onAddNewItem = {
                        val intent = Intent(this@SettingsActivity, EditItemActivity::class.java)
                        startActivity(intent)
                    },
                    onResetDefaults = {
                        AppConfig.resetDefaults(this@SettingsActivity)
                        val defaults = DashboardConfigManager.createDefaults()
                        DashboardConfigManager.save(this@SettingsActivity, defaults)
                        sectionsState.value = defaults
                        XrayTileService.requestRefresh(this@SettingsActivity)
                        try {
                            TileService.requestListeningState(this@SettingsActivity, ComponentName(this@SettingsActivity, RecordTileService::class.java))
                        } catch (ignored: Exception) {}
                        Toast.makeText(this@SettingsActivity, R.string.msg_defaults_restored, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sectionsState.value = DashboardConfigManager.load(this)
    }
}

class ImportState : ViewModel() {
    val pending = mutableStateOf<ConfigCodec.Snapshot?>(null)
}

@Composable
fun SettingsScreen(
    sections: List<DashboardSection>,
    transferBusy: Boolean,
    hasPendingImport: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onBack: () -> Unit,
    onItemClick: (DashboardItem, String) -> Unit,
    onAddNewItem: () -> Unit,
    onResetDefaults: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var isEditingRecordTile by remember { mutableStateOf(false) }
    var isSelectingRecordIcon by remember { mutableStateOf(false) }

    var recordLabel by remember { mutableStateOf(AppConfig.getRecordTileLabel(context)) }
    var recordIcon by remember { mutableStateOf(AppConfig.getRecordTileIcon(context)) }
    var recordCmd by remember { mutableStateOf(AppConfig.getRecordTileCommand(context)) }

    BackHandler(enabled = isSelectingRecordIcon) {
        isSelectingRecordIcon = false
    }
    BackHandler(enabled = isEditingRecordTile && !isSelectingRecordIcon) {
        isEditingRecordTile = false
    }

    AnimatedContent(
        targetState = Pair(isEditingRecordTile, isSelectingRecordIcon),
        transitionSpec = {
            if (targetState.second || (!initialState.first && targetState.first)) {
                (slideInHorizontally { it } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it / 3 } + fadeOut()
                )
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
                    slideOutHorizontally { it } + fadeOut()
                )
            }
        },
        label = "SettingsNavTransition"
    ) { (editingRecordTile, selectingRecordIcon) ->
        if (selectingRecordIcon) {
            IconPickerSubScreen(
                currentIcon = recordIcon,
                onSelectIcon = { newIcon ->
                    recordIcon = newIcon
                    isSelectingRecordIcon = false
                },
                onBack = { isSelectingRecordIcon = false }
            )
        } else if (editingRecordTile) {
            EditRecordTileScreen(
                label = recordLabel,
                onLabelChange = { recordLabel = it },
                icon = recordIcon,
                onOpenIconPicker = { isSelectingRecordIcon = true },
                command = recordCmd,
                onCommandChange = { recordCmd = it },
                onBack = { isEditingRecordTile = false },
                onSave = {
                    AppConfig.setRecordTileLabel(context, recordLabel.trim())
                    AppConfig.setRecordTileIcon(context, recordIcon)
                    AppConfig.setRecordTileCommand(context, recordCmd.trim())
                    try {
                        TileService.requestListeningState(context, ComponentName(context, RecordTileService::class.java))
                    } catch (ignored: Exception) {}
                    Toast.makeText(context, R.string.msg_record_tile_saved, Toast.LENGTH_SHORT).show()
                    isEditingRecordTile = false
                }
            )
        } else {
            SettingsMainContent(
                sections = sections,
                transferBusy = transferBusy,
                hasPendingImport = hasPendingImport,
                recordLabel = recordLabel,
                recordIcon = recordIcon,
                onOpenRecordTileSettings = {
                    recordLabel = AppConfig.getRecordTileLabel(context)
                    recordIcon = AppConfig.getRecordTileIcon(context)
                    recordCmd = AppConfig.getRecordTileCommand(context)
                    isEditingRecordTile = true
                },
                onImport = onImport,
                onExport = onExport,
                onConfirmImport = onConfirmImport,
                onCancelImport = onCancelImport,
                onBack = onBack,
                onItemClick = onItemClick,
                onAddNewItem = onAddNewItem,
                onResetDefaults = {
                    onResetDefaults()
                    recordLabel = AppConfig.getRecordTileLabel(context)
                    recordIcon = AppConfig.getRecordTileIcon(context)
                    recordCmd = AppConfig.getRecordTileCommand(context)
                }
            )
        }
    }
}

@Composable
fun EditRecordTileScreen(
    label: String,
    onLabelChange: (String) -> Unit,
    icon: String,
    onOpenIconPicker: () -> Unit,
    command: String,
    onCommandChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val resources = androidx.compose.ui.platform.LocalResources.current
    val iconLabels = remember(resources) {
        IconHelper.AVAILABLE_ICONS.associateWith { resources.getString(IconHelper.getLabelRes(it)) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_record_tile_settings),
                largeTitle = stringResource(R.string.title_record_tile_settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(MiuixIcons.Ok, contentDescription = stringResource(R.string.btn_save))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item(key = "record_general_header") {
                SmallTitle(text = stringResource(R.string.title_general))
            }
            item(key = "record_general_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextField(
                            value = label,
                            onValueChange = onLabelChange,
                            label = stringResource(R.string.label_record_tile_title),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item(key = "record_icon_header") {
                SmallTitle(text = stringResource(R.string.hint_item_icon))
            }
            item(key = "record_icon_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.label_selected_icon),
                        summary = iconLabels[icon] ?: icon,
                        startAction = {
                            Icon(
                                painter = painterResource(IconHelper.getDrawableRes(icon)),
                                contentDescription = null
                            )
                        },
                        onClick = onOpenIconPicker
                    )
                }
            }

            item(key = "record_cmd_header") {
                SmallTitle(text = stringResource(R.string.title_commands))
            }
            item(key = "record_cmd_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextField(
                            value = command,
                            onValueChange = onCommandChange,
                            label = stringResource(R.string.label_record_tile_cmd),
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMainContent(
    sections: List<DashboardSection>,
    transferBusy: Boolean,
    hasPendingImport: Boolean,
    recordLabel: String,
    recordIcon: String,
    onOpenRecordTileSettings: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onBack: () -> Unit,
    onItemClick: (DashboardItem, String) -> Unit,
    onAddNewItem: () -> Unit,
    onResetDefaults: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_settings),
                largeTitle = stringResource(R.string.title_settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item(key = "config_transfer_header") {
                SmallTitle(text = stringResource(R.string.title_config_transfer))
            }
            item(key = "config_transfer_card") {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    if (transferBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    ArrowPreference(
                        title = stringResource(R.string.action_import_config),
                        summary = stringResource(R.string.desc_import_config),
                        enabled = !transferBusy && !hasPendingImport,
                        onClick = onImport
                    )
                    ArrowPreference(
                        title = stringResource(R.string.action_export_config),
                        summary = stringResource(R.string.desc_export_config),
                        enabled = !transferBusy && !hasPendingImport,
                        onClick = onExport
                    )
                    if (hasPendingImport) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = stringResource(R.string.confirm_import_config), fontSize = 14.sp)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                                TextButton(text = stringResource(R.string.btn_cancel), enabled = !transferBusy, onClick = onCancelImport)
                                TextButton(text = stringResource(R.string.action_import_config), enabled = !transferBusy, onClick = onConfirmImport)
                            }
                        }
                    }
                }
            }

            item(key = "dashboard_items_header") {
                SmallTitle(text = stringResource(R.string.title_dashboard_items))
            }
            item(key = "dashboard_items_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    sections.forEach { section ->
                        section.items.forEach { item ->
                            val typeStr = when {
                                item.isCore -> stringResource(R.string.type_core_switch)
                                DashboardItem.TYPE_SWITCH.equals(item.type, ignoreCase = true) -> stringResource(R.string.label_switch)
                                else -> stringResource(R.string.label_button)
                            }
                            val subtitle = "${section.getDisplayTitle(context)} • $typeStr"

                            ArrowPreference(
                                title = item.getDisplayTitle(context),
                                summary = subtitle,
                                onClick = { onItemClick(item, section.getDisplayTitle(context)) },
                                enabled = !transferBusy && !hasPendingImport,
                                startAction = {
                                    Icon(
                                        painter = painterResource(IconHelper.getDrawableRes(item.icon)),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }

                    // Add new item row
                    ArrowPreference(
                        title = stringResource(R.string.title_add_new_item),
                        summary = stringResource(R.string.desc_add_new_item),
                        onClick = onAddNewItem,
                        enabled = !transferBusy && !hasPendingImport,
                        startAction = {
                            Icon(MiuixIcons.Add, contentDescription = null)
                        }
                    )
                }
            }

            item(key = "quick_settings_tiles_header") {
                SmallTitle(text = stringResource(R.string.title_quick_settings_tiles))
            }
            item(key = "quick_settings_tiles_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = recordLabel.ifBlank { stringResource(R.string.title_record_tile_settings) },
                        summary = stringResource(R.string.desc_record_tile_settings),
                        enabled = !transferBusy && !hasPendingImport,
                        startAction = {
                            Icon(
                                painter = painterResource(IconHelper.getDrawableRes(recordIcon)),
                                contentDescription = null
                            )
                        },
                        onClick = onOpenRecordTileSettings
                    )
                }
            }

            item(key = "reset_header") {
                SmallTitle(text = stringResource(R.string.title_reset))
            }
            item(key = "reset_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    if (!showResetConfirm) {
                        ArrowPreference(
                            title = stringResource(R.string.btn_reset_defaults),
                            summary = stringResource(R.string.desc_reset_defaults),
                            enabled = !transferBusy && !hasPendingImport,
                            onClick = { showResetConfirm = true }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm_reset),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    text = stringResource(R.string.btn_cancel),
                                    onClick = { showResetConfirm = false }
                                )
                                Button(
                                    enabled = !transferBusy && !hasPendingImport,
                                    onClick = {
                                        showResetConfirm = false
                                        onResetDefaults()
                                    }
                                ) {
                                    Text(stringResource(R.string.title_reset))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
