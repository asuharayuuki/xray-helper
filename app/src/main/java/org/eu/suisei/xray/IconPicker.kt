package org.eu.suisei.xray

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun IconPickerSubScreen(
    currentIcon: String,
    onSelectIcon: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scrollBehavior = MiuixScrollBehavior()
    val gridState = rememberLazyGridState()
    val selectedId = remember(currentIcon) { MaterialIconCatalog.selectedId(currentIcon) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showStyles by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable {
        mutableStateOf(MaterialIconCatalog.styleOf(currentIcon) ?: IconStyle.SYMBOLS_OUTLINED)
    }

    val labels = remember(context, resources, style) {
        MaterialIconCatalog.names(context, style).associate { name ->
            val id = style.iconId(name)
            id to MaterialIconCatalog.label(resources, id)
        }
    }
    val filteredIds = remember(searchQuery, labels) {
        val query = searchQuery.trim().replace('_', ' ')
        labels.keys.filter { id ->
            labels.getValue(id).contains(query, ignoreCase = true) ||
                id.substringAfter(':').replace('_', ' ').contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_select_icon),
                largeTitle = stringResource(R.string.title_select_icon),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SmallTitle(text = stringResource(R.string.title_search))
                    Card {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                gridState.requestScrollToItem(0)
                            },
                            label = stringResource(R.string.hint_search_icons),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                        ArrowPreference(
                            title = stringResource(R.string.title_icon_style),
                            summary = style.displayName(resources),
                            onClick = { showStyles = !showStyles }
                        )
                    }
                }
            }
            if (showStyles) {
                items(IconStyle.entries, key = { "style/${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { option ->
                    RadioButtonPreference(
                        title = option.displayName(resources),
                        selected = style == option,
                        onClick = {
                            style = option
                            showStyles = false
                            gridState.requestScrollToItem(0)
                        }
                    )
                }
            }
            item(key = "current_icon", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SmallTitle(text = stringResource(R.string.title_current_selection))
                    Card {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DashboardIcon(
                                id = currentIcon,
                                modifier = Modifier.size(28.dp),
                                tint = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(text = dashboardIconLabel(currentIcon))
                                MaterialIconCatalog.styleOf(currentIcon)?.let { currentStyle ->
                                    Text(
                                        text = currentStyle.displayName(resources),
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "result_count", span = { GridItemSpan(maxLineSpan) }) {
                SmallTitle(text = stringResource(R.string.title_all_icons, filteredIds.size))
            }
            if (filteredIds.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.msg_no_icons),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            items(filteredIds, key = { "icon/$it" }) { id ->
                val selected = id == selectedId
                val colors = MiuixTheme.colorScheme
                val tint = if (selected) colors.primary else colors.onSurface
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
                        .selectable(selected = selected, onClick = { onSelectIcon(id) })
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DashboardIcon(id = id, modifier = Modifier.size(28.dp), tint = tint)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = labels.getValue(id),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = tint
                    )
                }
            }
        }
    }
}
