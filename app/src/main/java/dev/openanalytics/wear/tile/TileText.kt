package dev.openanalytics.wear.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import dev.openanalytics.wear.R
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/**
 * Tiles can only use system fonts, so tile text is drawn with the app's DM Sans
 * into small PNGs and placed as images. Each distinct (text, size, colour,
 * weight) becomes one image resource, shared by every place and timeline entry
 * that uses it. Images carry the text as their content description.
 */
class TileText(private val context: Context) {
    enum class Weight(val font: Int) {
        Regular(R.font.dm_sans_regular),
        Medium(R.font.dm_sans_medium),
        SemiBold(R.font.dm_sans_semibold),
    }

    private class Rendered(val resource: ResourceBuilders.ImageResource, val widthDp: Float, val heightDp: Float)

    private val density = context.resources.displayMetrics.density
    private val fontScale = context.resources.configuration.fontScale
    private val images = LinkedHashMap<String, Rendered>()

    /** One run of text as a single image. */
    fun text(value: String, sizeSp: Float, color: Int, weight: Weight = Weight.Regular): LayoutElement =
        image(value, sizeSp, color, weight, padDp = 1f)

    private fun image(value: String, sizeSp: Float, color: Int, weight: Weight, padDp: Float): LayoutElement {
        val id = "t" + Integer.toHexString("$value|$sizeSp|$color|$weight|$padDp".hashCode())
        val img = images.getOrPut(id) { render(value, sizeSp, color, weight, padDp) }
        return LayoutElementBuilders.Image.Builder()
            .setResourceId(id)
            .setWidth(dp(img.widthDp))
            .setHeight(dp(img.heightDp))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(value).build())
                    .build(),
            )
            .build()
    }

    /**
     * Text built from one image per character, for values that change often
     * (clock times) so many timeline entries share a handful of glyph images.
     */
    fun glyphs(value: String, sizeSp: Float, color: Int, weight: Weight = Weight.Regular): LayoutElement {
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setSemantics(ModifiersBuilders.Semantics.Builder().setContentDescription(value).build())
                    .build(),
            )
        // No side padding, so glyphs sit at their natural advance width.
        for (ch in value) row.addContent(image(ch.toString(), sizeSp, color, weight, padDp = 0f))
        return row.build()
    }

    /** Changes whenever the set of images changes, so the system refetches resources. */
    fun version(): String = Integer.toHexString(images.keys.sorted().joinToString(",").hashCode())

    fun resources(version: String = version()): ResourceBuilders.Resources {
        val b = ResourceBuilders.Resources.Builder().setVersion(version)
        images.forEach { (id, img) -> b.addIdToImageMapping(id, img.resource) }
        return b.build()
    }

    private val typefaces = HashMap<Weight, Typeface>()

    private fun render(value: String, sizeSp: Float, color: Int, weight: Weight, padDp: Float): Rendered {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typefaces.getOrPut(weight) {
                ResourcesCompat.getFont(context, weight.font) ?: Typeface.DEFAULT
            }
            textSize = sizeSp * density * fontScale
            this.color = color
            // No "tnum": in DM Sans it also widens spaces, commas and colons.
        }
        val fm = paint.fontMetrics
        val pad = padDp * density
        val w = ceil(paint.measureText(value) + pad * 2).toInt().coerceAtLeast(1)
        val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(value, pad, -fm.ascent, paint)
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bmp.recycle()
        val resource = ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(png)
                    .setWidthPx(w)
                    .setHeightPx(h)
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED) // encoded PNG
                    .build(),
            )
            .build()
        return Rendered(resource, w / density, h / density)
    }
}
