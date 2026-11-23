package com.hibernador.app.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.hibernador.app.ui.theme.Indigo
import com.hibernador.app.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Anel de progresso com animação suave e gradiente. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    content: @Composable BoxScope.(animated: Float) -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "ring",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Indigo, Teal, Indigo)),
                        startAngle = 0f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content(animated)
    }
}

/** Barra horizontal arredondada e animada. */
@Composable
fun RamBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bar",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        if (animated > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Teal, Indigo)),
                size = Size(size.width * animated, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
    }
}

private object IconCache {
    val cache = LruCache<String, ImageBitmap>(64)
}

/** Ícone do app, carregado em segundo plano. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState(IconCache.cache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val px = (96 * context.resources.displayMetrics.density / 2).toInt().coerceAtLeast(48)
                    context.packageManager.getApplicationIcon(packageName)
                        .toBitmap(px, px, Bitmap.Config.ARGB_8888)
                        .asImageBitmap()
                        .also { IconCache.cache.put(packageName, it) }
                }.getOrNull()
            }
        }
    }
    val m = modifier.size(size)
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = m)
    } else {
        Box(m.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
