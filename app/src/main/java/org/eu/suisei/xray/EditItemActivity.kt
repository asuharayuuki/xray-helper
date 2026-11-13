package org.eu.suisei.xray

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.eu.suisei.xray.ui.MiuixAppTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.UUID

class EditItemActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_DEFAULT_SECTION = "extra_default_section"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val targetItemId = intent.getStringExtra(EXTRA_ITEM_ID)
        val defaultSection = intent.getStringExtra(EXTRA_DEFAULT_SECTION) ?: getString(R.string.default_section_actions)

        val sections = DashboardConfigManager.load(this)
        var editingItem: DashboardItem? = null
        var parentSection: DashboardSection? = null

        if (targetItemId != null) {
            for (sec in sections) {
                for (item in sec.items) {
                    if (item.id == targetItemId) {
                        editingItem = item
                        parentSection = sec
                        break
                    }
                }
                if (editingItem != null) break
            }
        }

        setContent {
            MiuixAppTheme {
                EditItemScreen(
                    initialItem = editingItem,
                    initialSectionTitle = parentSection?.getDisplayTitle(this) ?: if (editingItem?.isCore == true) getString(R.string.default_section_proxy) else defaultSection,
                    onBack = { finish() },
                    onSave = { updatedItem, targetSectionTitle, httpProxy ->
                        saveItem(updatedItem, targetSectionTitle, parentSection, httpProxy)
                    },
                    onDelete = { itemToDelete ->
                        deleteItem(itemToDelete)
                    }
                )
            }
        }
    }

    private fun saveItem(item: DashboardItem, targetSectionTitle: String, originalParent: DashboardSection?, httpProxy: String) {
        if (item.title.isBlank()) {
            Toast.makeText(this, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val effectiveSectionTitle = if (targetSectionTitle.isNotBlank()) {
            targetSectionTitle.trim()
        } else {
            getString(if (item.isCore) R.string.default_section_proxy else R.string.default_section_actions)
        }

        val sections = DashboardConfigManager.load(this)

        // Find or create target section
        var targetSection = sections.find { it.getDisplayTitle(this).equals(effectiveSectionTitle, ignoreCase = true) }
        if (targetSection == null) {
            targetSection = DashboardSection(UUID.randomUUID().toString(), effectiveSectionTitle, mutableListOf())
            sections.add(targetSection)
        }

        if (item.isCore) {
            // Save core commands directly to AppConfig
            if (!item.onCommand.isNullOrBlank()) AppConfig.setStartCommand(this, item.onCommand)
            if (!item.offCommand.isNullOrBlank()) AppConfig.setStopCommand(this, item.offCommand)
            AppConfig.setProcessName(this, if (!item.stateCheckCommand.isNullOrBlank()) item.stateCheckCommand else "xray")
            AppConfig.setHttpProxy(this, httpProxy.trim().ifEmpty { AppConfig.DEFAULT_HTTP_PROXY })

            // Update item in Dashboard config
            if (originalParent != null && originalParent.id != targetSection.id) {
                sections.find { it.id == originalParent.id }?.items?.removeAll { it.id == item.id }
                targetSection.items.add(item)
            } else {
                for (sec in sections) {
                    val idx = sec.items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) {
                        sec.items[idx] = item
                        break
                    }
                }
            }

            DashboardConfigManager.save(this, sections)
            XrayTileService.requestRefresh(this)
            Toast.makeText(this, R.string.msg_core_saved, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Custom items
        val isNew = originalParent == null && sections.none { s -> s.items.any { it.id == item.id } }
        if (isNew) {
            targetSection.items.add(item)
        } else {
            if (originalParent != null && originalParent.id != targetSection.id) {
                sections.find { it.id == originalParent.id }?.items?.removeAll { it.id == item.id }
                targetSection.items.add(item)
            } else {
                for (sec in sections) {
                    val idx = sec.items.indexOfFirst { it.id == item.id }
                    if (idx >= 0) {
                        sec.items[idx] = item
                        break
                    }
                }
            }
        }

        DashboardConfigManager.save(this, sections)
        Toast.makeText(this, R.string.msg_item_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun deleteItem(item: DashboardItem) {
        if (item.isCore) return
        val sections = DashboardConfigManager.load(this)
        for (sec in sections) {
            sec.items.removeAll { it.id == item.id }
        }
        DashboardConfigManager.save(this, sections)
        Toast.makeText(this, R.string.msg_item_deleted, Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
fun EditItemScreen(
    initialItem: DashboardItem?,
    initialSectionTitle: String,
    onBack: () -> Unit,
    onSave: (DashboardItem, String, String) -> Unit,
    onDelete: (DashboardItem) -> Unit
) {
    val isCore = initialItem?.isCore == true
    val isEditing = initialItem != null

    val pageTitle = when {
        isCore -> stringResource(R.string.title_edit_core)
        isEditing -> stringResource(R.string.dialog_title_edit_item)
        else -> stringResource(R.string.title_new_item)
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    var title by remember { mutableStateOf(initialItem?.getDisplayTitle(context) ?: "") }
    var sectionTitle by remember { mutableStateOf(initialSectionTitle) }
    var isSwitch by remember {
        mutableStateOf(
            if (isCore) true
            else DashboardItem.TYPE_SWITCH.equals(initialItem?.type ?: DashboardItem.TYPE_BUTTON, ignoreCase = true)
        )
    }
    var selectedIcon by remember {
        mutableStateOf(initialItem?.icon ?: if (isCore) "shield" else "terminal")
    }

    // Commands
    var onCmd by remember {
        mutableStateOf(if (isCore) AppConfig.getStartCommand(context) else (initialItem?.onCommand ?: ""))
    }
    var offCmd by remember {
        mutableStateOf(if (isCore) AppConfig.getStopCommand(context) else (initialItem?.offCommand ?: ""))
    }
    var processName by remember {
        mutableStateOf(if (isCore) AppConfig.getProcessName(context) else "")
    }
    var httpProxy by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(if (isCore) AppConfig.getHttpProxy(context) else "")
    }
    var stateCheckCmd by remember { mutableStateOf(initialItem?.stateCheckCommand ?: "") }
    var summaryCmd by remember { mutableStateOf(initialItem?.summaryCommand ?: "") }
    var disableConditionCmd by remember { mutableStateOf(initialItem?.disableConditionCommand ?: "") }

    var isSelectingIcon by remember { mutableStateOf(false) }

    // Intercept back gesture when icon subpage is open
    BackHandler(enabled = isSelectingIcon) {
        isSelectingIcon = false
    }

    AnimatedContent(
        targetState = isSelectingIcon,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally { it } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it / 3 } + fadeOut()
                )
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(
                    slideOutHorizontally { it } + fadeOut()
                )
            }
        },
        label = "IconPickerPageTransition"
    ) { pickingIcon ->
        if (pickingIcon) {
            IconPickerSubScreen(
                currentIcon = selectedIcon,
                onSelectIcon = { newIcon ->
                    selectedIcon = newIcon
                    isSelectingIcon = false
                },
                onBack = { isSelectingIcon = false }
            )
        } else {
            EditItemMainContent(
                pageTitle = pageTitle,
                isCore = isCore,
                isEditing = isEditing,
                title = title,
                onTitleChange = { title = it },
                sectionTitle = sectionTitle,
                onSectionTitleChange = { sectionTitle = it },
                isSwitch = isSwitch,
                onSwitchChange = { isSwitch = it },
                selectedIcon = selectedIcon,
                onOpenIconPicker = { isSelectingIcon = true },
                onCmd = onCmd,
                onCmdChange = { onCmd = it },
                offCmd = offCmd,
                offCmdChange = { offCmd = it },
                processName = processName,
                onProcessNameChange = { processName = it },
                httpProxy = httpProxy,
                onHttpProxyChange = { httpProxy = it },
                stateCheckCmd = stateCheckCmd,
                onStateCheckCmdChange = { stateCheckCmd = it },
                summaryCmd = summaryCmd,
                onSummaryCmdChange = { summaryCmd = it },
                disableConditionCmd = disableConditionCmd,
                onDisableConditionCmdChange = { disableConditionCmd = it },
                onBack = onBack,
                onSave = {
                    val id = initialItem?.id ?: UUID.randomUUID().toString()
                    val type = if (isCore || isSwitch) DashboardItem.TYPE_SWITCH else DashboardItem.TYPE_BUTTON
                    val savedItem = DashboardItem(
                        id,
                        title.trim(),
                        type,
                        selectedIcon,
                        onCmd.trim(),
                        offCmd.trim(),
                        if (isCore) processName.trim() else stateCheckCmd.trim(),
                        summaryCmd.trim(),
                        disableConditionCmd.trim(),
                        isCore
                    )
                    if (initialItem != null && title.trim() == initialItem.getDisplayTitle(context)) {
                        savedItem.title = initialItem.title
                        savedItem.titleKey = initialItem.titleKey
                    }
                    onSave(savedItem, sectionTitle.trim(), httpProxy)
                },
                onDelete = {
                    if (initialItem != null) {
                        onDelete(initialItem)
                    }
                },
                deleteItemTitle = initialItem?.getDisplayTitle(context)
            )
        }
    }
}

@Composable
fun EditItemMainContent(
    pageTitle: String,
    isCore: Boolean,
    isEditing: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    sectionTitle: String,
    onSectionTitleChange: (String) -> Unit,
    isSwitch: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    selectedIcon: String,
    onOpenIconPicker: () -> Unit,
    onCmd: String,
    onCmdChange: (String) -> Unit,
    offCmd: String,
    offCmdChange: (String) -> Unit,
    processName: String,
    onProcessNameChange: (String) -> Unit,
    httpProxy: String,
    onHttpProxyChange: (String) -> Unit,
    stateCheckCmd: String,
    onStateCheckCmdChange: (String) -> Unit,
    summaryCmd: String,
    onSummaryCmdChange: (String) -> Unit,
    disableConditionCmd: String,
    onDisableConditionCmdChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    deleteItemTitle: String?
) {
    val scrollBehavior = MiuixScrollBehavior()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
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
            // General Info
            item(key = "header_general") {
                SmallTitle(text = stringResource(R.string.title_general))
            }
            item(key = "card_general") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextField(
                            value = title,
                            onValueChange = onTitleChange,
                            label = stringResource(R.string.label_title),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = sectionTitle,
                            onValueChange = onSectionTitleChange,
                            label = stringResource(R.string.label_section_name),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!isCore) {
                        SmallTitle(text = stringResource(R.string.label_control_type))
                        RadioButtonPreference(
                            title = stringResource(R.string.label_switch),
                            selected = isSwitch,
                            onClick = { onSwitchChange(true) }
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.label_action_button),
                            selected = !isSwitch,
                            onClick = { onSwitchChange(false) }
                        )
                    }
                }
            }

            // Icon Selection
            item(key = "header_icon") {
                SmallTitle(text = stringResource(R.string.hint_item_icon))
            }
            item(key = "card_icon") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.label_selected_icon),
                        summary = dashboardIconLabel(selectedIcon),
                        startAction = {
                            DashboardIcon(
                                id = selectedIcon,
                                contentDescription = null
                            )
                        },
                        onClick = onOpenIconPicker
                    )
                }
            }

            // Commands
            item(key = "header_commands") {
                SmallTitle(text = stringResource(R.string.title_commands))
            }
            item(key = "card_commands") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (isCore) {
                            TextField(
                                value = onCmd,
                                onValueChange = onCmdChange,
                                label = stringResource(R.string.label_start_cmd),
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = offCmd,
                                onValueChange = offCmdChange,
                                label = stringResource(R.string.label_stop_cmd),
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = processName,
                                onValueChange = onProcessNameChange,
                                label = stringResource(R.string.hint_process_name),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = httpProxy,
                                onValueChange = onHttpProxyChange,
                                label = stringResource(R.string.label_proxy_address),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.hint_http_proxy_default),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        } else {
                            if (isSwitch) {
                                TextField(
                                    value = onCmd,
                                    onValueChange = onCmdChange,
                                    label = stringResource(R.string.label_on_command),
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextField(
                                    value = offCmd,
                                    onValueChange = offCmdChange,
                                    label = stringResource(R.string.label_off_command),
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextField(
                                    value = stateCheckCmd,
                                    onValueChange = onStateCheckCmdChange,
                                    label = stringResource(R.string.label_state_check),
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                TextField(
                                    value = onCmd,
                                    onValueChange = onCmdChange,
                                    label = stringResource(R.string.label_action_command),
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = summaryCmd,
                                onValueChange = onSummaryCmdChange,
                                label = stringResource(R.string.label_summary_command),
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = disableConditionCmd,
                                onValueChange = onDisableConditionCmdChange,
                                label = stringResource(R.string.hint_disable_condition_command),
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Delete item button (only for existing custom items)
            if (isEditing && !isCore) {
                item(key = "header_danger") {
                    SmallTitle(text = stringResource(R.string.title_danger_zone))
                }
                item(key = "card_delete") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        if (!showDeleteConfirm) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    text = stringResource(R.string.action_delete_item),
                                    onClick = { showDeleteConfirm = true }
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.confirm_delete_item, deleteItemTitle ?: stringResource(R.string.label_this_item)),
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        text = stringResource(R.string.btn_cancel),
                                        onClick = { showDeleteConfirm = false }
                                    )
                                    Button(
                                        onClick = {
                                            showDeleteConfirm = false
                                            onDelete()
                                        }
                                    ) {
                                        Text(stringResource(R.string.btn_delete))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
