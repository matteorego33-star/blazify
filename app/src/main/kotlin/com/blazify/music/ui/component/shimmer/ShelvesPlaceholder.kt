/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * The shape of a mood page while it loads: a heading, a row of big cards, a
 * heading, a list of songs — what is about to arrive, drawn where it will be.
 *
 * Brighter than the stock placeholders and with no fade at its foot: on a
 * black page those faded to nearly nothing, and the wait read as a black
 * screen rather than as loading.
 */
@Composable
fun ShelvesPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val card = LocalConfiguration.current.screenWidthDp.dp * 0.42f
    ShimmerHost(modifier = modifier.fillMaxWidth(), showGradient = false) {
        repeat(2) {
            Heading(block)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                repeat(3) {
                    Column(Modifier.width(card)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(SkeletonCorner))
                                .background(block),
                        )
                        Bar(block, Modifier.padding(top = 10.dp).fillMaxWidth(0.8f), 14)
                        Bar(block, Modifier.padding(top = 6.dp).fillMaxWidth(0.55f), 12)
                    }
                }
            }
            Heading(block)
            repeat(4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(SkeletonCorner))
                            .background(block),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Bar(block, Modifier.width(170.dp), 14)
                        Bar(block, Modifier.padding(top = 6.dp).width(110.dp), 12)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(20.dp))
    Bar(color, Modifier.padding(start = 16.dp, bottom = 14.dp).width(150.dp), 24)
}

@Composable
private fun Bar(color: androidx.compose.ui.graphics.Color, modifier: Modifier, height: Int) {
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(SkeletonCorner))
            .background(color),
    )
}

/**
 * The shape of a page that opens on a grid — a genre, a mood with no shelves of its own:
 * big square covers two to a row, each with its title and a line beneath.
 */
@Composable
fun CardGridPlaceholder(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    ShimmerHost(modifier = modifier.fillMaxWidth(), showGradient = false) {
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                repeat(2) {
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(SkeletonCorner))
                                .background(block),
                        )
                        Bar(block, Modifier.padding(top = 10.dp).fillMaxWidth(0.75f), 14)
                        Bar(block, Modifier.padding(top = 6.dp).fillMaxWidth(0.5f), 12)
                    }
                }
            }
        }
    }
}
