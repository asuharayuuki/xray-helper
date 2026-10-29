package org.eu.suisei.xray

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.eu.suisei.xray.ui.MiuixAppTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private val isEvaluating = AtomicBoolean(false)
    private val isBusy = mutableStateOf(false)
    private val sectionsState = mutableStateOf<List<DashboardSection>>(emptyList())
    private val switchStates = mutableStateMapOf<String, Boolean>()
    private val summaryStates = mutableStateMapOf<String, String>()
    private val disabledStates = mutableStateMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!shouldHandleIntent(intent)) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MiuixAppTheme {
                MainScreen(
                    sections = sectionsState.value,
                    isBusy = isBusy.value,
                    switchStates = switchStates,
                    summaryStates = summaryStates,
                    disabledStates = disabledStates,
                    onRefresh = { refreshDashboardStates() },
                    onOpenSettings = {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    },
                    onToggleSwitch = { item, targetState ->
                        handleSwitchToggle(item, targetState)
                    },
                    onButtonClick = { item ->
                        handleButtonClick(item)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!shouldHandleIntent(intent)) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        reloadConfiguration()
        refreshDashboardStates()
    }

    private fun reloadConfiguration() {
        sectionsState.value = DashboardConfigManager.load(this)
    }

    private fun shouldHandleIntent(intent: Intent?): Boolean {
        if (intent == null) return true
        if (TileService.ACTION_QS_TILE_PREFERENCES == intent.action) {
            var component: ComponentName? = null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    component = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    val extra = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
                    if (extra != null) component = extra
                }
                if (component == null) {
                    val extraStr = intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME)
                    if (extraStr != null) {
                        component = ComponentName.unflattenFromString(extraStr)
                    }
                }
            } catch (ignored: Exception) {
            }

            if (component != null && XrayTileService::class.java.name != component.className) {
                return false
            }
        }
        return true
    }

    private fun handleSwitchToggle(item: DashboardItem, targetState: Boolean) {
        if (isBusy.value) return
        if (item.isCore) {
            isBusy.value = true
            thread(name = "Core-Toggle") {
                if (targetState) {
                    RootShell.startService(this)
                } else {
                    RootShell.stopService(this)
                }
                XrayTileService.requestRefresh(this)
                runOnUiThread {
                    isBusy.value = false
                    refreshDashboardStates()
                }
            }
        } else {
            val command = if (targetState) item.onCommand else item.offCommand
            executeCommandAsync(command) {
                refreshDashboardStates()
            }
        }
    }

    private fun handleButtonClick(item: DashboardItem) {
        executeCommandAsync(item.onCommand) {
            refreshDashboardStates()
        }
    }

    private fun refreshDashboardStates() {
        if (!isEvaluating.compareAndSet(false, true)) {
            return
        }

        isBusy.value = true

        thread(name = "Dashboard-StateEvaluator") {
            try {
                // 1. Evaluate Core Status
                val coreStatus = RootShell.queryDetailedStatus(this)
                val configuredProxy = AppConfig.getHttpProxy(this)

                val isProxyActive = configuredProxy == coreStatus.httpProxy
                val isCoreRunning = !coreStatus.pid.isNullOrEmpty()

                val coreSummaryText: String = when {
                    !coreStatus.isRootGranted -> getString(R.string.state_root_denied)
                    coreStatus.state == RootShell.State.ERROR -> getString(R.string.state_core_error)
                    isCoreRunning && isProxyActive -> getString(R.string.state_core_on)
                    isCoreRunning -> getString(R.string.state_core_running_proxy_off)
                    isProxyActive -> getString(R.string.state_core_exited)
                    else -> getString(R.string.state_core_off)
                }

                // 2. Evaluate Other Items
                val newSwitches = HashMap<String, Boolean>()
                val newSummaries = HashMap<String, String>()
                val newDisabled = HashMap<String, Boolean>()

                val sections = sectionsState.value
                for (section in sections) {
                    for (item in section.items) {
                        if (item.isCore) {
                            newSwitches[item.id] = isProxyActive
                            newSummaries[item.id] = coreSummaryText
                            continue
                        }

                        // Switch state check
                        if (DashboardItem.TYPE_SWITCH.equals(item.type, ignoreCase = true)) {
                            if (!item.stateCheckCommand.isNullOrBlank()) {
                                val on = executeCheckExitCode(item.stateCheckCommand) == 0
                                newSwitches[item.id] = on
                            }
                        }

                        // Disable condition check
                        if (!item.disableConditionCommand.isNullOrBlank()) {
                            val disabled = executeCheckExitCode(item.disableConditionCommand) == 0
                            newDisabled[item.id] = disabled
                        }

                        // Dynamic summary command
                        if (!item.summaryCommand.isNullOrBlank()) {
                            val summary = executeGetOutput(item.summaryCommand)
                            if (summary.isNotBlank()) {
                                newSummaries[item.id] = summary
                            }
                        }
                    }
                }

                runOnUiThread {
                    switchStates.clear()
                    switchStates.putAll(newSwitches)

                    summaryStates.clear()
                    summaryStates.putAll(newSummaries)

                    disabledStates.clear()
                    disabledStates.putAll(newDisabled)

                    isBusy.value = false
                    isEvaluating.set(false)
                    XrayTileService.requestRefresh(this)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isBusy.value = false
                    isEvaluating.set(false)
                }
            }
        }
    }

    private fun executeCheckExitCode(command: String): Int = try {
        CommandRunner.run(buildShellProcess(command), 3000, 0).exitCode
    } catch (e: Exception) { -1 }

    private fun executeGetOutput(command: String): String = try {
        val result = CommandRunner.run(buildShellProcess(command), 3000, 200)
        if (result.timedOut) "" else result.output.trim().replace('\n', ' ')
    } catch (e: Exception) { "" }

    private fun executeCommandAsync(command: String?, onComplete: (() -> Unit)? = null) {
        if (command.isNullOrBlank()) {
            onComplete?.invoke()
            return
        }
        if (isBusy.value) return
        isBusy.value = true
        thread(name = "CustomAction-Executor") {
            try {
                val result = CommandRunner.run(buildShellProcess(command), 10000, 2000)
                runOnUiThread {
                    val message = when {
                        result.timedOut -> getString(R.string.msg_command_timeout)
                        result.output.isNotBlank() -> result.output.trim().take(200)
                        result.exitCode != 0 -> getString(R.string.msg_command_failed, result.exitCode)
                        else -> null
                    }
                    if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, R.string.msg_command_error, Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread {
                    isBusy.value = false
                    onComplete?.invoke()
                }
            }
        }
    }

    companion object {
        private fun buildShellProcess(command: String?): ProcessBuilder {
            val trimmed = command?.trim() ?: ""
            return if (trimmed.startsWith("su ") || trimmed == "su") {
                ProcessBuilder("/system/bin/sh", "-c", trimmed)
            } else {
                ProcessBuilder("su", "-c", trimmed)
            }
        }
    }
}

@Composable
fun MainScreen(
    sections: List<DashboardSection>,
    isBusy: Boolean,
    switchStates: Map<String, Boolean>,
    summaryStates: Map<String, String>,
    disabledStates: Map<String, Boolean>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSwitch: (DashboardItem, Boolean) -> Unit,
    onButtonClick: (DashboardItem) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = stringResource(R.string.app_name),
                    largeTitle = stringResource(R.string.app_name),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.btn_refresh))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(MiuixIcons.Settings, contentDescription = stringResource(R.string.title_settings))
                        }
                    }
                )
                if (isBusy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            sections.forEach { section ->
                if (section.items.isNotEmpty()) {
                    item(key = "header_${section.id}") {
                        SmallTitle(text = section.getDisplayTitle(context))
                    }
                    item(key = "card_${section.id}") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            section.items.forEach { item ->
                                val isSwitch = DashboardItem.TYPE_SWITCH.equals(item.type, ignoreCase = true)
                                val isChecked = switchStates[item.id] ?: false
                                val summary = summaryStates[item.id]
                                val isDisabled = disabledStates[item.id] ?: false

                                val startAction: @Composable (() -> Unit)? = if (!item.icon.isNullOrBlank() && !item.icon.equals("none", ignoreCase = true)) {
                                    {
                                        Icon(
                                            painter = painterResource(IconHelper.getDrawableRes(item.icon)),
                                            contentDescription = null
                                        )
                                    }
                                } else null

                                if (isSwitch) {
                                    SwitchPreference(
                                        title = item.getDisplayTitle(context),
                                        summary = if (!summary.isNullOrBlank()) summary else null,
                                        checked = isChecked,
                                        onCheckedChange = { nextState ->
                                            onToggleSwitch(item, nextState)
                                        },
                                        startAction = startAction,
                                        enabled = !isDisabled && !isBusy
                                    )
                                } else {
                                    ArrowPreference(
                                        title = item.getDisplayTitle(context),
                                        summary = if (!summary.isNullOrBlank()) summary else null,
                                        onClick = {
                                            if (!isDisabled) {
                                                onButtonClick(item)
                                            }
                                        },
                                        startAction = startAction,
                                        enabled = !isDisabled && !isBusy
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
