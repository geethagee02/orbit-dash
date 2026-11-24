package dev.geetha.orbitdash.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Orbit Dash, in numbers.
 *
 * The planet sits at the origin. The ship rides the circle of radius [SHIP_ORBIT].
 * Arcs are annular slices that fall inward. A tap flips the sign of the ship's
 * angular velocity. Overlap in both radius and angle is a collision. An arc that
 * slips past the orbit outside that angle is a dodge and adds points.
 *
 * [step] is one frame. The same world, flip, and dt always produce the same world.
 * A hit checked in a slice cancels dodges that would have landed in that same slice.
 */
object OrbitRules {
    const val TAU: Float = (PI * 2.0).toFloat()
    const val SHIP_ORBIT = 1f
    const val SHIP_RADIAL_HALF = 0.055f
    const val SHIP_ANGULAR_HALF = 0.07f
    const val PLANET_RADIUS = 0.42f
    const val SPAWN_OUTER = 1.95f
    const val ARC_THICKNESS = 0.20f
    const val GAP_MARGIN = 0.40f
    const val OPENING_GAP = 1.15f
    const val MAX_FRAME_SECONDS = 0.25f

    private const val SLICE_SECONDS = 0.02f
    private const val ANGULAR_BASE = 1.35f
    private const val ANGULAR_RISE = 1.45f
    private const val INBOUND_BASE = 0.26f
    private const val INBOUND_RISE = 0.42f
    private const val SPAWN_FLOOR = 0.46f
    private const val SPAWN_RISE = 0.90f
    private const val ANGULAR_EASE = 12f
    private const val INBOUND_EASE = 14f
    private const val SPAWN_EASE = 10f

    fun wrapAngle(radians: Float): Float {
        val turns = radians % TAU
        return if (turns < 0f) turns + TAU else turns
    }

    fun angularDistance(a: Float, b: Float): Float {
        val delta = abs(wrapAngle(a) - wrapAngle(b))
        return min(delta, TAU - delta)
    }

    fun angularSpeed(dodges: Int): Float = ANGULAR_BASE + ANGULAR_RISE * ease(dodges, ANGULAR_EASE)

    fun inboundSpeed(dodges: Int): Float = INBOUND_BASE + INBOUND_RISE * ease(dodges, INBOUND_EASE)

    fun spawnInterval(dodges: Int): Float = SPAWN_FLOOR + SPAWN_RISE * exp(-dodges.coerceAtLeast(0) / SPAWN_EASE)

    /** 0 at the opening pace, approaching 1 as the orbit speeds up. */
    fun pace(dodges: Int): Float = ease(dodges, ANGULAR_EASE)

    fun dodgePoints(streak: Int): Int {
        val steps = (streak.coerceAtLeast(1) - 1).coerceAtMost(8)
        return 10 + steps * 5
    }

    fun isNewBest(score: Int, stored: Int): Boolean = score > stored

    fun shownBest(stored: Int, run: Int): Int = max(stored, run)

    /**
     * A pilot name, shared with the leaderboard server.
     * 1–16 characters after trim, letters or digits plus space, hyphen, dot,
     * and apostrophe, and at least one letter or digit.
     */
    fun canonicalName(raw: String): String? {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.length !in 1..16) return null
        if (trimmed.none { it.isLetterOrDigit() }) return null
        val allowed = trimmed.all { ch ->
            ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '.' || ch == '\''
        }
        return if (allowed) trimmed else null
    }

    fun collides(shipAngle: Float, arc: Arc): Boolean {
        if (arc.cleared) return false
        val shipInner = SHIP_ORBIT - SHIP_RADIAL_HALF
        val shipOuter = SHIP_ORBIT + SHIP_RADIAL_HALF
        val radial = shipInner <= arc.outer && arc.inner <= shipOuter
        if (!radial) return false
        return angularDistance(shipAngle, arc.center) <= arc.halfSpan + SHIP_ANGULAR_HALF
    }

    fun placeCenter(
        shipAngle: Float,
        dodges: Int,
        existing: List<Arc>,
        unit: Float,
        halfSpan: Float,
    ): Float {
        var center = wrapAngle(unit * TAU)
        if (dodges < 2) center = keepOffShip(center, shipAngle, unit)
        val blockers = existing.filter { !it.cleared && it.outer > SHIP_ORBIT }
        repeat(5) {
            val blocker = blockers.firstOrNull { overlaps(center, halfSpan, it) } ?: return center
            center = wrapAngle(
                blocker.center + blocker.halfSpan + halfSpan + SHIP_ANGULAR_HALF * 2f + GAP_MARGIN,
            )
        }
        if (dodges < 2 && angularDistance(center, shipAngle) < OPENING_GAP) {
            center = wrapAngle(shipAngle + PI.toFloat())
        }
        return center
    }

    fun nextUnit(state: Long): Pair<Long, Float> {
        var x = state
        if (x == 0L) x = 0x4F52424954L
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        val unit = ((x ushr 40) and 0xFFFFFFL).toFloat() / 16777216f
        return x to unit
    }

    fun step(world: World, flip: Boolean, dt: Float): World {
        if (!world.alive) return world
        val total = dt.coerceIn(0f, MAX_FRAME_SECONDS)
        if (total == 0f) return stepSlice(world, flip, 0f)
        val slices = ceil(total / SLICE_SECONDS).toInt().coerceAtLeast(1)
        val slice = total / slices
        var current = world
        var pendingFlip = flip
        repeat(slices) {
            current = stepSlice(current, pendingFlip, slice)
            pendingFlip = false
            if (!current.alive) return current
        }
        return current
    }

    private fun stepSlice(world: World, flip: Boolean, dt: Float): World {
        if (!world.alive) return world
        val direction = signedDirection(world.direction).let { if (flip) -it else it }
        val angle = wrapAngle(world.shipAngle + direction * angularSpeed(world.dodges) * dt)
        val inbound = inboundSpeed(world.dodges)
        val moved = world.arcs.map { arc ->
            if (arc.cleared) arc
            else arc.copy(inner = arc.inner - inbound * dt, outer = arc.outer - inbound * dt)
        }
        if (moved.any { collides(angle, it) }) {
            return world.copy(
                shipAngle = angle,
                direction = direction,
                arcs = moved,
                alive = false,
                elapsed = world.elapsed + dt,
            )
        }

        var dodges = world.dodges
        var streak = world.streak
        var score = world.score
        val clearLine = SHIP_ORBIT - SHIP_RADIAL_HALF
        val resolved = moved.map { arc ->
            if (!arc.cleared && arc.outer < clearLine) {
                dodges += 1
                streak += 1
                score += dodgePoints(streak)
                arc.copy(cleared = true)
            } else {
                arc
            }
        }
        val kept = resolved.filter { it.outer > PLANET_RADIUS }.toMutableList()

        var spawnIn = world.spawnIn - dt
        var nextId = world.nextId
        var rng = world.rng
        if (spawnIn <= 0f) {
            val (rngA, unit) = nextUnit(rng)
            val (rngB, widthUnit) = nextUnit(rngA)
            rng = rngB
            val halfSpan = 0.30f + 0.22f * widthUnit
            val center = placeCenter(angle, dodges, kept, unit, halfSpan)
            kept += Arc(
                id = nextId,
                center = center,
                halfSpan = halfSpan,
                inner = SPAWN_OUTER - ARC_THICKNESS,
                outer = SPAWN_OUTER,
            )
            nextId += 1
            spawnIn = spawnInterval(dodges)
        }

        return world.copy(
            shipAngle = angle,
            direction = direction,
            arcs = kept,
            dodges = dodges,
            streak = streak,
            score = score,
            alive = true,
            nextId = nextId,
            spawnIn = spawnIn,
            elapsed = world.elapsed + dt,
            rng = rng,
        )
    }

    private fun keepOffShip(center: Float, shipAngle: Float, unit: Float): Float {
        if (angularDistance(center, shipAngle) >= OPENING_GAP) return center
        return wrapAngle(shipAngle + OPENING_GAP + unit * (TAU - 2f * OPENING_GAP))
    }

    private fun overlaps(center: Float, halfSpan: Float, other: Arc): Boolean {
        val need = halfSpan + other.halfSpan + SHIP_ANGULAR_HALF * 2f + GAP_MARGIN
        return angularDistance(center, other.center) < need
    }

    private fun signedDirection(direction: Int): Int = if (direction < 0) -1 else 1

    private fun ease(dodges: Int, scale: Float): Float {
        return 1f - exp(-dodges.coerceAtLeast(0) / scale)
    }
}

data class Arc(
    val id: Long,
    val center: Float,
    val halfSpan: Float,
    val inner: Float,
    val outer: Float,
    val cleared: Boolean = false,
)

data class World(
    val shipAngle: Float = 0f,
    val direction: Int = 1,
    val arcs: List<Arc> = emptyList(),
    val dodges: Int = 0,
    val streak: Int = 0,
    val score: Int = 0,
    val alive: Boolean = true,
    val nextId: Long = 1L,
    val spawnIn: Float = 0.85f,
    val elapsed: Float = 0f,
    val rng: Long = 0x4F52424954L,
)
