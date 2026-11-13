package org.eu.suisei.xray

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import java.util.Locale
import kotlin.math.roundToInt

internal enum class IconStyle(
    val id: String,
    @get:StringRes val label: Int,
    @get:FontRes val font: Int = 0,
    val codepoints: String = "symbols"
) {
    SYMBOLS_OUTLINED("symbols_outlined", R.string.icon_style_outlined, R.font.symbols_outlined),
    SYMBOLS_ROUNDED("symbols_rounded", R.string.icon_style_rounded, R.font.symbols_rounded),
    SYMBOLS_SHARP("symbols_sharp", R.string.icon_style_sharp, R.font.symbols_sharp),
    SYMBOLS_OUTLINED_FILLED("symbols_outlined_filled", R.string.icon_style_outlined_filled, R.font.symbols_outlined_filled),
    SYMBOLS_ROUNDED_FILLED("symbols_rounded_filled", R.string.icon_style_rounded_filled, R.font.symbols_rounded_filled),
    SYMBOLS_SHARP_FILLED("symbols_sharp_filled", R.string.icon_style_sharp_filled, R.font.symbols_sharp_filled),
    ICONS_FILLED("icons_filled", R.string.icon_style_filled, R.font.icons_filled, "icons_filled"),
    ICONS_OUTLINED("icons_outlined", R.string.icon_style_outlined, R.font.icons_outlined, "icons_outlined"),
    ICONS_ROUNDED("icons_rounded", R.string.icon_style_rounded, R.font.icons_rounded, "icons_rounded"),
    ICONS_SHARP("icons_sharp", R.string.icon_style_sharp, R.font.icons_sharp, "icons_sharp"),
    ICONS_TWO_TONE("icons_two_tone", R.string.icon_style_two_tone, R.font.icons_two_tone, "icons_two_tone"),
    BUILT_IN("built_in", R.string.icon_style_built_in);

    fun iconId(name: String) = if (this == BUILT_IN) name else "$id:$name"

    fun displayName(resources: Resources): String {
        val style = resources.getString(label)
        if (this == BUILT_IN) return style
        val family = if (codepoints == "symbols") "Material Symbols" else "Material Icons"
        return resources.getString(R.string.icon_style_label, family, style)
    }
}

internal object MaterialIconCatalog {
    private val styles = IconStyle.entries.filter { it != IconStyle.BUILT_IN }.associateBy { it.id }
    private val codepointMaps = mutableMapOf<String, Map<String, Int>>()

    @Synchronized
    private fun codepoints(context: Context, style: IconStyle): Map<String, Int> =
        codepointMaps.getOrPut(style.codepoints) {
            context.assets.open("material_icons/${style.codepoints}.codepoints").bufferedReader().useLines { lines ->
                buildMap {
                    for (line in lines) {
                        val separator = line.indexOf(' ')
                        // The classic Filled catalog contains a duplicate alias.
                        putIfAbsent(line.substring(0, separator), line.substring(separator + 1).trim().toInt(16))
                    }
                }.toSortedMap()
            }
        }

    fun styleOf(id: String): IconStyle? = styles[id.substringBefore(':', "")]

    fun names(context: Context, style: IconStyle): List<String> =
        if (style == IconStyle.BUILT_IN) IconHelper.AVAILABLE_ICONS.toList()
        else codepoints(context, style).keys.toList()

    fun label(resources: Resources, id: String): String {
        val style = styleOf(id) ?: return resources.getString(IconHelper.getLabelRes(id))
        return id.removePrefix("${style.id}:").replace('_', ' ')
    }

    fun glyph(context: Context, id: String): IconGlyph? {
        val style = styleOf(id) ?: return null
        val name = id.substringAfter(':')
        val codepoint = codepoints(context, style)[name] ?: return null
        return IconGlyph(String(Character.toChars(codepoint)), context.resources.getFont(style.font))
    }

    fun selectedId(id: String): String {
        if (styleOf(id) != null) return id
        val name = id.lowercase(Locale.ROOT)
        // Prefer the original name when two entries use the same drawable.
        if (name in IconHelper.AVAILABLE_ICONS) return name
        val drawable = IconHelper.getDrawableRes(name)
        return IconHelper.AVAILABLE_ICONS.firstOrNull { IconHelper.getDrawableRes(it) == drawable }
            ?: name
    }

    @JvmStatic
    fun tileIcon(context: Context, id: String): Icon {
        val glyph = glyph(context, id)
            ?: return Icon.createWithResource(context, IconHelper.getDrawableRes(id))
        // Send a small monochrome bitmap so System UI can apply its tile tint.
        val size = (24 * context.resources.displayMetrics.density).roundToInt()
        return Icon.createWithBitmap(glyph.bitmap(size, size))
    }
}

internal class IconGlyph(private val text: String, private val typeface: Typeface) {
    fun bitmap(width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = this@IconGlyph.typeface
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = minOf(width, height) * 0.96f
        }
        // Google icon fonts use a square em above the baseline.
        Canvas(bitmap).drawText(text, width / 2f, (height + paint.textSize) / 2f, paint)
        return bitmap
    }
}
