/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import com.blazify.music.R

/**
 * What every cover shows while it loads, and in place of one that is missing: a quiet tile
 * with the Blazify logo in black and white in the middle. It used to be the music-note icon
 * stretched edge to edge, which read as a broken picture rather than one on its way.
 *
 * Set once on the app's image loader, so every cover in the app gets it.
 */
class ArtPlaceholderDrawable(context: Context) : Drawable() {
    private val dark =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val logo: Bitmap? = Logo.get(context)
    private val tile = Paint().apply { color = if (dark) 0xFF1C1A19.toInt() else 0xFFE9E5E1.toInt() }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        alpha = if (dark) 120 else 95
    }
    private val dst = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b, tile)
        val logo = logo ?: return
        // Half the cover's shorter side (the logo image has room round it): clear on a song row, quiet on the player.
        val side = (minOf(b.width(), b.height()) * 0.5f).toInt()
        val cx = b.centerX()
        val cy = b.centerY()
        dst.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        canvas.drawBitmap(logo, null, dst, mark)
    }

    override fun setAlpha(alpha: Int) {
        tile.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE

    /** The logo is decoded once and shared by every placeholder. */
    private object Logo {
        @Volatile private var bitmap: Bitmap? = null

        fun get(context: Context): Bitmap? =
            bitmap ?: runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.blaze_logo)
            }.getOrNull()?.also { bitmap = it }
    }
}
