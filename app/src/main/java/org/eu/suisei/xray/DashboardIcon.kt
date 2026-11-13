package org.eu.suisei.xray

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun dashboardIconLabel(id: String): String = MaterialIconCatalog.label(LocalResources.current, id)

@Composable
internal fun DashboardIcon(
    id: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MiuixTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    val glyph = remember(context, id) { MaterialIconCatalog.glyph(context, id) }
    if (glyph == null) {
        Icon(
            painter = painterResource(IconHelper.getDrawableRes(id)),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
        return
    }

    val accessibility = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Box(
        modifier.size(24.dp).then(accessibility).drawWithCache {
            val width = size.width.roundToInt().coerceAtLeast(1)
            val height = size.height.roundToInt().coerceAtLeast(1)
            val image = glyph.bitmap(width, height).asImageBitmap()
            val colorFilter = ColorFilter.tint(tint)
            onDrawBehind { drawImage(image, colorFilter = colorFilter) }
        }
    )
}
