package org.eu.suisei.xray

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity : ComponentActivity() {

    private val sectionsState = mutableStateOf<List<DashboardSection>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MiuixAppTheme {
                SettingsScreen(
                    sections = sectionsState.value,
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

@Composable
fun SettingsScreen(
    sections: List<DashboardSection>,
    onBack: () -> Unit,
    onItemClick: (DashboardItem, String) -> Unit,
    onAddNewItem: () -> Unit,
    onResetDefaults: () -> Unit
) {
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
                        Icon(MiuixIcons.Back, contentDescription = "Back")
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
            item(key = "dashboard_items_header") {
                SmallTitle(text = "Dashboard items")
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
                                item.isCore -> "Core switch"
                                DashboardItem.TYPE_SWITCH.equals(item.type, ignoreCase = true) -> "Switch"
                                else -> "Button"
                            }
                            val subtitle = "${section.title} • $typeStr"

                            ArrowPreference(
                                title = item.title,
                                summary = subtitle,
                                onClick = { onItemClick(item, section.title) },
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
                        title = "Add new item",
                        summary = "Create custom switch or action button",
                        onClick = onAddNewItem,
                        startAction = {
                            Icon(MiuixIcons.Add, contentDescription = null)
                        }
                    )
                }
            }

            item(key = "reset_header") {
                SmallTitle(text = "Reset")
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
                            summary = "Restore default commands and dashboard layout",
                            onClick = { showResetConfirm = true }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Reset all Xray commands and dashboard layout to defaults?",
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
                                    onClick = {
                                        showResetConfirm = false
                                        onResetDefaults()
                                    }
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
