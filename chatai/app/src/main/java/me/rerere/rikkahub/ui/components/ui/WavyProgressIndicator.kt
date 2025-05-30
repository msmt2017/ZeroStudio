package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 确定进度的波浪线性进度条
 */
@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 2.dp,
    waveLength: Dp = 20.dp,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { waveAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }

    Canvas(
        modifier = modifier
            .progressSemantics(progress)
            .height(strokeWidth * 3)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2

        // Draw background track
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(canvasWidth, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        val progressEnd = progress * canvasWidth

        // Draw wavy progress line
        val path = Path()
        var x = 0f
        path.moveTo(0f, centerY)

        while (x <= progressEnd) {
            val waveY =
                centerY + amplitudePx * sin(x * (2f * PI / waveLengthPx)).toFloat()
            path.lineTo(x, waveY)
            x += 1f
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}

/**
 * 不确定进度的波浪线性进度条，2/3是波浪，1/3是背景
 */
@Composable
fun WavyLinearProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 2.dp,
    waveLength: Dp = 20.dp,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { waveAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "WavyTransition")

    // Animate phase from 0 to cover the full scroll distance (canvasWidth + waveSegmentWidth)
    // IMPORTANT: Call animateFloat outside the Canvas drawScope
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f, // Animate a normalized value (0 to 1)
        animationSpec = infiniteRepeatable(
            // Adjust duration for desired speed
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PhaseAnimation"
    )

    Canvas(
        modifier = modifier
            .progressSemantics()
            .height(strokeWidth * 3) // Adjust height to accommodate amplitude
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2
        val waveSegmentWidth = canvasWidth * (2f / 3f)

        // Calculate the actual distance the phase needs to cover
        val scrollDistance = canvasWidth + waveSegmentWidth
        // Map the normalized phase (0 to 1) to the actual scroll distance
        val currentScrollPosition = phase * scrollDistance

        // Draw background track
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(canvasWidth, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )

        // Draw the moving wave segment
        val path = Path()
        var firstVisiblePointSet = false

        // Iterate through the x-coordinates covered by the wave segment
        // Use a step for performance, can be adjusted
        val step = 1f
        var currentX = currentScrollPosition - waveSegmentWidth
        while(currentX < currentScrollPosition) {
            // Only draw points within the canvas bounds
            if (currentX >= 0f && currentX <= canvasWidth) {
                val waveY = centerY + amplitudePx * sin((currentX * (2f * PI / waveLengthPx)).toFloat())
                if (!firstVisiblePointSet) {
                    path.moveTo(currentX, waveY)
                    firstVisiblePointSet = true
                } else {
                    path.lineTo(currentX, waveY)
                }
            }
            currentX += step
        }
        // Ensure the last point connects smoothly if it's visible
        if(currentScrollPosition > 0 && currentScrollPosition <= canvasWidth && firstVisiblePointSet) {
             val waveY = centerY + amplitudePx * sin((currentScrollPosition * (2f * PI / waveLengthPx)).toFloat())
             path.lineTo(currentScrollPosition, waveY)
        }


        // Draw the wave path if any part was visible
        if (firstVisiblePointSet) {
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 确定进度的波浪圆形进度条
 */
@Composable
fun WavyCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 1.dp,
    waveCount: Int = 9,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { waveAmplitude.toPx() }

    val progressAnimation by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "ProgressAnimation"
    )

    Canvas(
        modifier = modifier
            .progressSemantics(progress)
            .size(40.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val radius = (minOf(canvasWidth, canvasHeight) - strokeWidthPx) / 2
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val startAngle = 270f

        // 计算波浪路径点 - 用于背景和进度
        val wavePoints = mutableListOf<Offset>()
        for (angle in 0..360) {
            val radians = (angle + startAngle) * PI.toFloat() / 180f
            val waveRadiusOffset = amplitudePx * sin(angle * waveCount * PI.toFloat() / 180f)
            val x = center.x + (radius + waveRadiusOffset) * kotlin.math.cos(radians)
            val y = center.y + (radius + waveRadiusOffset) * kotlin.math.sin(radians)
            wavePoints.add(Offset(x, y))
        }

        // 绘制背景波浪轨道
        val backgroundPath = Path()
        backgroundPath.moveTo(wavePoints[0].x, wavePoints[0].y)
        for (i in 1 until wavePoints.size) {
            backgroundPath.lineTo(wavePoints[i].x, wavePoints[i].y)
        }
        backgroundPath.close()
        
        drawPath(
            path = backgroundPath,
            color = trackColor,
            style = Stroke(width = strokeWidthPx)
        )

        // 计算进度对应的角度
        val progressAngle = (progressAnimation * 360f).toInt()
        
        // 只有在有进度时才绘制进度波浪
        if (progressAngle > 0) {
            // 绘制进度波浪
            val progressPath = Path()
            progressPath.moveTo(wavePoints[0].x, wavePoints[0].y)
            
            // 只绘制到进度对应的角度
            val endIndex = minOf(progressAngle, wavePoints.size - 1)
            for (i in 1..endIndex) {
                progressPath.lineTo(wavePoints[i].x, wavePoints[i].y)
            }
            
            drawPath(
                path = progressPath,
                color = color,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 不确定进度的波浪圆形进度条
 */
@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 1.dp,
    waveCount: Int = 9,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { waveAmplitude.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "WavyCircularTransition")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CircularRotation"
    )

    Canvas(
        modifier = modifier
            .progressSemantics()
            .size(40.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val radius = (minOf(canvasWidth, canvasHeight) - strokeWidthPx) / 2
        val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
        val startAngle = 270f

        // 计算波浪路径点 - 用于背景
        val wavePoints = mutableListOf<Offset>()
        for (angle in 0..360) {
            val radians = (angle + startAngle) * PI.toFloat() / 180f
            val waveRadiusOffset = amplitudePx * sin(angle * waveCount * PI.toFloat() / 180f)
            val x = center.x + (radius + waveRadiusOffset) * kotlin.math.cos(radians)
            val y = center.y + (radius + waveRadiusOffset) * kotlin.math.sin(radians)
            wavePoints.add(Offset(x, y))
        }

        // 绘制背景波浪轨道
        val backgroundPath = Path()
        backgroundPath.moveTo(wavePoints[0].x, wavePoints[0].y)
        for (i in 1 until wavePoints.size) {
            backgroundPath.lineTo(wavePoints[i].x, wavePoints[i].y)
        }
        backgroundPath.close()
        
        drawPath(
            path = backgroundPath,
            color = trackColor,
            style = Stroke(width = strokeWidthPx)
        )

        // 不确定进度指示器 - 绘制部分波浪作为进度
        val sweepAngle = 240f
        val offsetAngle = rotationAngle.toInt() % 360
        
        val progressPath = Path()
        val startIndex = offsetAngle
        progressPath.moveTo(wavePoints[startIndex % 361].x, wavePoints[startIndex % 361].y)
        
        for (i in 1..sweepAngle.toInt()) {
            val index = (startIndex + i) % 361
            progressPath.lineTo(wavePoints[index].x, wavePoints[index].y)
        }
        
        drawPath(
            path = progressPath,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
    }
}

// 使用示例
@Composable
@Preview(showBackground = true, showSystemUi = true)
fun ProgressIndicatorExample() {
    var progress by remember { mutableFloatStateOf(0.5f) }
    Column(
        modifier = Modifier
            .safeContentPadding()
            .padding(16.dp)
            .clickable {
                progress += 0.1f
                if (progress > 1f) progress = 0f
            },
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(progress.toString())

        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // 确定进度的波浪进度条
        Text("Wavy Linear - Determinate")
        WavyLinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth()
        )

        // 不确定进度的波浪进度条
        Text("Wavy Linear - Indeterminate")
        WavyLinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
        )

        CircularProgressIndicator()

        // 确定进度的波浪圆形进度条
        Text("Wavy Circular - Determinate")
        WavyCircularProgressIndicator(
            progress = progress,
            modifier = Modifier,
        )

        // 不确定进度的波浪圆形进度条
        Text("Wavy Circular - Indeterminate")
        WavyCircularProgressIndicator(
            modifier = Modifier,
        )
    }
}