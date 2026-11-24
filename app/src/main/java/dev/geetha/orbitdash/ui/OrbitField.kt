package dev.geetha.orbitdash.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.tooling.preview.Preview
import dev.geetha.orbitdash.domain.Arc
import dev.geetha.orbitdash.domain.OrbitRules
import dev.geetha.orbitdash.domain.World
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class Star(
    val x: Float,
    val y: Float,
    val layer: Int,
    val radius: Float,
    val alpha: Float,
)

fun starfield(seed: Long = 0x51ED): List<Star> {
    var state = seed
    fun unit(): Float {
        val (next, value) = OrbitRules.nextUnit(state)
        state = next
        return value
    }
    return List(96) {
        val roll = unit()
        val layer = when {
            roll > 0.84f -> 2
            roll > 0.55f -> 1
            else -> 0
        }
        Star(
            x = unit(),
            y = unit(),
            layer = layer,
            radius = 0.7f + unit() * (1.1f + layer),
            alpha = 0.28f + unit() * 0.6f,
        )
    }
}

@Composable
fun OrbitField(
    world: World,
    stars: List<Star>,
    trail: List<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (size.minDimension < 2f) return@Canvas
        drawRect(Ink)
        val origin = Offset(size.width / 2f, size.height * 0.46f)
        val nearest = min(
            min(origin.x, size.width - origin.x),
            min(origin.y, size.height - origin.y),
        )
        val orbitPx = nearest * 0.62f
        drawStars(stars, world)
        world.arcs.sortedByDescending { it.outer }.forEach { arc ->
            hazardArc(arc, origin, orbitPx)
        }
        planet(origin, orbitPx * OrbitRules.PLANET_RADIUS)
        drawCircle(
            color = Paper.copy(alpha = 0.22f),
            radius = orbitPx,
            center = origin,
            style = Stroke(width = 1.5f),
        )
        directionMark(world, origin, orbitPx)
        trailDots(trail, origin, orbitPx)
        ship(world, origin, orbitPx)
        if (!world.alive) {
            val impact = polar(origin, orbitPx, world.shipAngle)
            drawCircle(Coral.copy(alpha = 0.35f), orbitPx * 0.09f, impact)
            drawCircle(Paper.copy(alpha = 0.9f), orbitPx * 0.018f, impact)
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Ink.copy(alpha = 0.78f)),
                center = origin,
                radius = size.maxDimension * 0.72f,
            ),
        )
    }
}

private fun DrawScope.drawStars(stars: List<Star>, world: World) {
    val scale = size.minDimension / 360f
    stars.forEach { star ->
        val drift = world.elapsed * (0.004f + star.layer * 0.007f) +
            world.shipAngle * 0.012f * (star.layer + 1)
        val x = wrap01(star.x + drift)
        val y = wrap01(star.y + world.elapsed * 0.0015f * (star.layer + 1))
        val center = Offset(x * size.width, y * size.height)
        val radius = star.radius * scale
        drawCircle(
            color = Paper.copy(alpha = star.alpha * (0.4f + 0.22f * star.layer)),
            radius = radius,
            center = center,
        )
        if (star.layer == 2 && star.alpha > 0.7f) {
            val arm = radius * 3.2f
            drawLine(Paper.copy(alpha = 0.45f), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), 1f)
            drawLine(Paper.copy(alpha = 0.45f), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), 1f)
        }
    }
}

private fun DrawScope.planet(origin: Offset, radius: Float) {
    drawIntoCanvas { canvas ->
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(80, 61, 220, 181)
            maskFilter = BlurMaskFilter(radius * 0.45f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.nativeCanvas.drawCircle(origin.x, origin.y, radius * 1.35f, glow)
    }
    val disc = Path().apply { addOval(Rect(origin, radius)) }
    clipPath(disc) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFD7F7EF), Color(0xFF1C6E73), Color(0xFF0C242C)),
                center = Offset(origin.x - radius * 0.36f, origin.y - radius * 0.42f),
                radius = radius * 1.45f,
            ),
            radius = radius,
            center = origin,
        )
        drawCircle(
            color = Color(0xFF071820).copy(alpha = 0.55f),
            radius = radius * 0.95f,
            center = Offset(origin.x + radius * 0.34f, origin.y + radius * 0.38f),
        )
        drawArc(
            color = Paper.copy(alpha = 0.16f),
            startAngle = 18f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(origin.x - radius * 0.72f, origin.y - radius * 0.08f),
            size = Size(radius * 1.44f, radius * 0.28f),
            style = Stroke(width = 1.5f),
        )
    }
    drawCircle(
        color = Teal.copy(alpha = 0.45f),
        radius = radius * 1.08f,
        center = origin,
        style = Stroke(width = radius * 0.07f),
    )
}

private fun DrawScope.hazardArc(arc: Arc, origin: Offset, scale: Float) {
    val mid = ((arc.inner + arc.outer) / 2f) * scale
    val weight = (arc.outer - arc.inner).coerceAtLeast(0.04f) * scale
    val faded = arc.cleared
    ringArc(origin, mid, arc.center, arc.halfSpan, Coral.copy(alpha = if (faded) 0.10f else 0.28f), weight + scale * 0.07f)
    ringArc(origin, mid, arc.center, arc.halfSpan, Coral.copy(alpha = if (faded) 0.32f else 0.95f), weight)
}

private fun DrawScope.directionMark(world: World, origin: Offset, orbitPx: Float) {
    if (!world.alive) return
    val dir = if (world.direction < 0) -1f else 1f
    ringArc(
        origin = origin,
        radius = orbitPx,
        midAngle = world.shipAngle + dir * 0.30f,
        halfSpan = 0.22f,
        color = Amber.copy(alpha = 0.9f),
        strokeWidth = 3.5f,
    )
}

private fun DrawScope.trailDots(trail: List<Float>, origin: Offset, orbitPx: Float) {
    if (trail.isEmpty()) return
    val oldestFirst = trail.asReversed()
    oldestFirst.forEachIndexed { index, angle ->
        val t = (index + 1f) / oldestFirst.size
        drawCircle(
            color = Amber.copy(alpha = 0.06f + 0.55f * t),
            radius = orbitPx * (0.008f + 0.014f * t),
            center = polar(origin, orbitPx, angle),
        )
    }
}

private fun DrawScope.ship(world: World, origin: Offset, orbitPx: Float) {
    val dir = if (world.direction < 0) -1f else 1f
    val heading = Offset(-sin(world.shipAngle) * dir, -cos(world.shipAngle) * dir)
    val side = Offset(-heading.y, heading.x)
    val pos = polar(origin, orbitPx, world.shipAngle)
    val nose = orbitPx * 0.058f
    val tail = orbitPx * 0.036f
    val wing = orbitPx * 0.042f
    val tip = pos + heading * nose
    val back = pos - heading * tail
    val notch = pos - heading * (tail * 0.4f)
    if (world.alive) {
        val engine = pos - heading * (tail * 0.85f)
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(190, 255, 183, 3)
                maskFilter = BlurMaskFilter(orbitPx * 0.045f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawCircle(engine.x, engine.y, orbitPx * 0.028f, paint)
        }
    }
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo((back + side * wing).x, (back + side * wing).y)
        lineTo(notch.x, notch.y)
        lineTo((back - side * wing).x, (back - side * wing).y)
        close()
    }
    drawPath(path, if (world.alive) Paper else Coral)
}

private fun DrawScope.ringArc(
    origin: Offset,
    radius: Float,
    midAngle: Float,
    halfSpan: Float,
    color: Color,
    strokeWidth: Float,
) {
    val start = Math.toDegrees((-(midAngle + halfSpan)).toDouble()).toFloat()
    val sweep = Math.toDegrees((halfSpan * 2f).toDouble()).toFloat()
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(origin.x - radius, origin.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}

private fun polar(origin: Offset, radius: Float, angle: Float): Offset {
    return Offset(origin.x + cos(angle) * radius, origin.y - sin(angle) * radius)
}

private fun wrap01(value: Float): Float {
    val wrapped = value % 1f
    return if (wrapped < 0f) wrapped + 1f else wrapped
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun FieldPreview() {
    OrbitTheme {
        OrbitField(
            world = World(
                shipAngle = 0.4f,
                arcs = listOf(Arc(1, 2.4f, 0.42f, 1.35f, 1.55f)),
            ),
            stars = starfield(),
            trail = listOf(0.40f, 0.34f, 0.28f, 0.22f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
