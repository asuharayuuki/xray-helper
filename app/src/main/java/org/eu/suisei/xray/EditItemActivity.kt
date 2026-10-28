package org.eu.suisei.xray

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import top.yukonga.miuix.kmp.window.WindowDialog
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
        val defaultSection = intent.getStringExtra(EXTRA_DEFAULT_SECTION) ?: "Actions"

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
                    initialSectionTitle = parentSection?.title ?: if (editingItem?.isCore == true) "Proxy" else defaultSection,
                    onBack = { finish() },
                    onSave = { updatedItem, targetSectionTitle ->
                        saveItem(updatedItem, targetSectionTitle, parentSection)
                    },
                    onDelete = { itemToDelete ->
                        deleteItem(itemToDelete)
                    }
                )
            }
        }
    }

    private fun saveItem(item: DashboardItem, targetSectionTitle: String, originalParent: DashboardSection?) {
        if (item.title.isBlank()) {
            Toast.makeText(this, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val effectiveSectionTitle = if (targetSectionTitle.isNotBlank()) {
            targetSectionTitle.trim()
        } else {
            if (item.isCore) "Proxy" else "Actions"
        }

        val sections = DashboardConfigManager.load(this)

        // Find or create target section
        var targetSection = sections.find { it.title.equals(effectiveSectionTitle, ignoreCase = true) }
        if (targetSection == null) {
            targetSection = DashboardSection(UUID.randomUUID().toString(), effectiveSectionTitle, mutableListOf())
            sections.add(targetSection)
        }

        if (item.isCore) {
            // Save core commands directly to AppConfig
            if (!item.onCommand.isNullOrBlank()) AppConfig.setStartCommand(this, item.onCommand)
            if (!item.offCommand.isNullOrBlank()) AppConfig.setStopCommand(this, item.offCommand)
            AppConfig.setProcessName(this, if (!item.stateCheckCommand.isNullOrBlank()) item.stateCheckCommand else "xray")

            // Update item in Dashboard config
            if (originalParent != null && !originalParent.title.equals(effectiveSectionTitle, ignoreCase = true)) {
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
            Toast.makeText(this, "Core settings saved", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Custom items
        val isNew = originalParent == null && sections.none { s -> s.items.any { it.id == item.id } }
        if (isNew) {
            targetSection.items.add(item)
        } else {
            if (originalParent != null && !originalParent.title.equals(effectiveSectionTitle, ignoreCase = true)) {
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
        Toast.makeText(this, "Item saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun deleteItem(item: DashboardItem) {
        if (item.isCore) return
        val sections = DashboardConfigManager.load(this)
        for (sec in sections) {
            sec.items.removeAll { it.id == item.id }
        }
        DashboardConfigManager.save(this, sections)
        Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
fun EditItemScreen(
    initialItem: DashboardItem?,
    initialSectionTitle: String,
    onBack: () -> Unit,
    onSave: (DashboardItem, String) -> Unit,
    onDelete: (DashboardItem) -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val isCore = initialItem?.isCore == true
    val isEditing = initialItem != null

    val pageTitle = when {
        isCore -> "Edit Core"
        isEditing -> "Edit item"
        else -> "New item"
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
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
    var stateCheckCmd by remember { mutableStateOf(initialItem?.stateCheckCommand ?: "") }
    var summaryCmd by remember { mutableStateOf(initialItem?.summaryCommand ?: "") }
    var disableConditionCmd by remember { mutableStateOf(initialItem?.disableConditionCommand ?: "") }

    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
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
                            onSave(savedItem, sectionTitle.trim())
                        }
                    ) {
                        Icon(MiuixIcons.Ok, contentDescription = "Save")
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
                SmallTitle(text = "General")
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
                            onValueChange = { title = it },
                            label = "Title",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = sectionTitle,
                            onValueChange = { sectionTitle = it },
                            label = "Section name",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!isCore) {
                        SmallTitle(text = "Control type")
                        RadioButtonPreference(
                            title = "Switch",
                            selected = isSwitch,
                            onClick = { isSwitch = true }
                        )
                        RadioButtonPreference(
                            title = "Action Button",
                            selected = !isSwitch,
                            onClick = { isSwitch = false }
                        )
                    }
                }
            }

            // Icon Selection
            item(key = "header_icon") {
                SmallTitle(text = "Icon")
            }
            item(key = "card_icon") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = "Selected Icon",
                        summary = formatIconName(selectedIcon),
                        startAction = {
                            Icon(
                                painter = painterResource(IconHelper.getDrawableRes(selectedIcon)),
                                contentDescription = null
                            )
                        },
                        onClick = { showIconPicker = true }
                    )
                }
            }

            // Commands
            item(key = "header_commands") {
                SmallTitle(text = "Commands")
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
                                onValueChange = { onCmd = it },
                                label = "Start command",
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = offCmd,
                                onValueChange = { offCmd = it },
                                label = "Stop command",
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = processName,
                                onValueChange = { processName = it },
                                label = "Process name (Default: xray)",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            if (isSwitch) {
                                TextField(
                                    value = onCmd,
                                    onValueChange = { onCmd = it },
                                    label = "On command",
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextField(
                                    value = offCmd,
                                    onValueChange = { offCmd = it },
                                    label = "Off command",
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                TextField(
                                    value = stateCheckCmd,
                                    onValueChange = { stateCheckCmd = it },
                                    label = "State check command (Exit code 0 = Checked)",
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                TextField(
                                    value = onCmd,
                                    onValueChange = { onCmd = it },
                                    label = "Action command",
                                    singleLine = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = summaryCmd,
                                onValueChange = { summaryCmd = it },
                                label = "Dynamic summary command (Output = Subtitle)",
                                singleLine = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = disableConditionCmd,
                                onValueChange = { disableConditionCmd = it },
                                label = "Disable condition command (Exit code 0 = Disabled)",
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
                    SmallTitle(text = "Danger Zone")
                }
                item(key = "card_delete") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                text = "Delete item",
                                onClick = { showDeleteDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Icon Picker WindowDialog
    if (showIconPicker) {
        WindowDialog(
            show = showIconPicker,
            onDismissRequest = { showIconPicker = false },
            title = "Select icon"
        ) {
            val iconRows = remember { IconHelper.AVAILABLE_ICONS.toList().chunked(4) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iconRows.forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowIcons.forEach { iconName ->
                            val isSelected = iconName.equals(selectedIcon, ignoreCase = true)
                            val bg = if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .clickable {
                                        selectedIcon = iconName
                                        showIconPicker = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        painter = painterResource(IconHelper.getDrawableRes(iconName)),
                                        contentDescription = iconName,
                                        modifier = Modifier.size(26.dp),
                                        tint = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatIconName(iconName),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        repeat(4 - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = stringResource(R.string.btn_cancel),
                    onClick = { showIconPicker = false }
                )
            }
        }
    }

    // Delete Confirmation WindowDialog
    if (showDeleteDialog && initialItem != null) {
        WindowDialog(
            show = showDeleteDialog,
            onDismissRequest = { showDeleteDialog = false },
            title = "Delete item",
            summary = "Are you sure you want to delete '${initialItem.title}'?"
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = stringResource(R.string.btn_cancel),
                    onClick = { showDeleteDialog = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(initialItem)
                    }
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            }
        }
    }
}

private fun formatIconName(name: String?): String {
    if (name.isNullOrBlank()) return "Shield"
    val clean = name.replace('_', ' ')
    return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
