/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.utils

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tells the launch screen's logo when Home has settled. The logo glides into the header
 * only then: started any earlier it shares the main thread with Home laying out its
 * shelves, and stalls halfway.
 */
object LaunchSignal {
    val homeSettled = MutableStateFlow(false)
}
