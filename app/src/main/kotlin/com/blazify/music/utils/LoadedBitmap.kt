/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.utils

import android.graphics.Bitmap
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap

/**
 * The picture an image request loaded, as a bitmap, or null when it did not load.
 *
 * A failed load comes back carrying the error image instead of the picture. That is the Blazify
 * mark, drawn to fit whatever holds it, so it has no size of its own; turning it into a bitmap
 * threw, and took the whole app down whenever a cover could not be fetched — offline, say.
 * Reading colours from it was wrong anyway: they would have been the placeholder's, not the
 * song's.
 */
fun ImageResult?.loadedBitmap(): Bitmap? = (this as? SuccessResult)?.image?.toBitmap()
