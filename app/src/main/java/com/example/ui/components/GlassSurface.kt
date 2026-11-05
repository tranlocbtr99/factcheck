package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/**
 * Samples only the app's content layer. External-app overlays deliberately use an opaque material.
 */
@Composable
fun GlassSurface(
    backdrop: GraphicsLayer?,
    opaque: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    var position by remember { mutableStateOf(Offset.Zero) }
    val blurred = rememberGraphicsLayer()
    val tint = MaterialTheme.colorScheme.surface
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    Row(
        modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .clip(shape)
            .drawWithContent {
                if (backdrop != null && !opaque && Build.VERSION.SDK_INT >= 31) {
                    blurred.record { translate(-position.x, -position.y) { drawLayer(backdrop) } }
                    blurred.renderEffect = BlurEffect(24.dp.toPx(), 24.dp.toPx(), TileMode.Clamp)
                    drawLayer(blurred)
                }
                drawRect(
                    tint.copy(
                        alpha =
                            if (opaque || Build.VERSION.SDK_INT < 31) 0.98f
                            else if (dark) 0.80f else 0.74f
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.10f else 0.55f),
                            Color.Transparent,
                        )
                    )
                )
                drawContent()
            }
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.8f),
                        tint.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.2f),
                    )
                ),
                shape,
            )
            .padding(6.dp),
        content = content,
    )
}
