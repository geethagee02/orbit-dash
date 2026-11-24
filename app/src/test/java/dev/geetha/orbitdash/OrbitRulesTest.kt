package dev.geetha.orbitdash

import dev.geetha.orbitdash.domain.Arc
import dev.geetha.orbitdash.domain.OrbitRules
import dev.geetha.orbitdash.domain.World
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitRulesTest {
    private val clearLine = OrbitRules.SHIP_ORBIT - OrbitRules.SHIP_RADIAL_HALF

    @Test
    fun wrapAndDistanceMeetAcrossTheSeam() {
        assertEquals(OrbitRules.TAU - 0.1f, OrbitRules.wrapAngle(-0.1f), 0.0001f)
        assertEquals(0.2f, OrbitRules.wrapAngle(OrbitRules.TAU + 0.2f), 0.001f)
        assertEquals(0.1f, OrbitRules.angularDistance(0.05f, OrbitRules.TAU - 0.05f), 0.0001f)
        assertEquals(PI.toFloat(), OrbitRules.angularDistance(0f, PI.toFloat()), 0.001f)
    }

    @Test
    fun collisionNeedsBothTheBandAndTheAngle() {
        val onTop = Arc(1, center = 0f, halfSpan = 0.4f, inner = 0.94f, outer = 1.12f)
        assertTrue(OrbitRules.collides(0.05f, onTop))
        val acrossTheSeam = onTop.copy(center = OrbitRules.TAU - 0.08f)
        assertTrue(OrbitRules.collides(0.02f, acrossTheSeam))
        assertFalse(OrbitRules.collides(PI.toFloat(), onTop))
        val stillOutside = onTop.copy(inner = 1.30f, outer = 1.50f)
        assertFalse(OrbitRules.collides(0f, stillOutside))
        val alreadyInside = onTop.copy(inner = 0.50f, outer = 0.70f)
        assertFalse(OrbitRules.collides(0f, alreadyInside))
        assertFalse(OrbitRules.collides(0f, onTop.copy(cleared = true)))
    }

    @Test
    fun flipReversesDirectionAndTheNextStep() {
        val start = World(shipAngle = 0.5f, spawnIn = 10f)
        val backward = OrbitRules.step(start, flip = true, dt = 0.1f)
        assertEquals(-1, backward.direction)
        assertTrue(backward.shipAngle < start.shipAngle)

        val forwardAgain = OrbitRules.step(backward, flip = true, dt = 0.1f)
        assertEquals(1, forwardAgain.direction)
        assertTrue(forwardAgain.shipAngle > backward.shipAngle)
    }

    @Test
    fun shipAdvancesWithTheSignOfDirection() {
        val ccw = OrbitRules.step(World(shipAngle = 0.5f, direction = 1, spawnIn = 10f), false, 0.1f)
        val cw = OrbitRules.step(World(shipAngle = 0.5f, direction = -1, spawnIn = 10f), false, 0.1f)
        assertEquals(0.635f, ccw.shipAngle, 0.01f)
        assertEquals(0.365f, cw.shipAngle, 0.01f)
    }

    @Test
    fun aMissThatPassesTheOrbitScoresAndClears() {
        val arc = Arc(
            id = 7,
            center = PI.toFloat(),
            halfSpan = 0.3f,
            inner = clearLine + 0.02f - 0.20f,
            outer = clearLine + 0.02f,
        )
        val next = OrbitRules.step(World(arcs = listOf(arc), spawnIn = 10f), false, 0.1f)
        assertTrue(next.alive)
        assertEquals(1, next.dodges)
        assertEquals(1, next.streak)
        assertEquals(10, next.score)
        assertTrue(next.arcs.single().cleared)
    }

    @Test
    fun theSecondDodgeInAStreakPaysMore() {
        val passing = Arc(
            id = 3,
            center = PI.toFloat() / 2f,
            halfSpan = 0.25f,
            inner = clearLine + 0.02f - 0.20f,
            outer = clearLine + 0.02f,
        )
        val next = OrbitRules.step(
            World(arcs = listOf(passing), dodges = 1, streak = 1, score = 10, spawnIn = 10f),
            flip = false,
            dt = 0.1f,
        )
        assertEquals(25, next.score)
        assertEquals(2, next.streak)
        assertEquals(15, OrbitRules.dodgePoints(2))
        assertEquals(50, OrbitRules.dodgePoints(9))
        assertEquals(50, OrbitRules.dodgePoints(40))
    }

    @Test
    fun twoSafeArcsScoreInOneStep() {
        val outer = clearLine + 0.02f
        val arcs = listOf(
            Arc(1, PI.toFloat(), 0.25f, outer - 0.20f, outer),
            Arc(2, PI.toFloat() / 2f, 0.25f, outer - 0.20f, outer),
        )
        val next = OrbitRules.step(World(arcs = arcs, spawnIn = 10f), false, 0.1f)
        assertTrue(next.alive)
        assertEquals(2, next.dodges)
        assertEquals(25, next.score)
    }

    @Test
    fun aHitFreezesTheScoreAndIgnoresADodgeInThatSlice() {
        val hit = Arc(1, center = 0f, halfSpan = 0.5f, inner = 0.90f, outer = 1.20f)
        val safe = Arc(
            id = 2,
            center = PI.toFloat(),
            halfSpan = 0.25f,
            inner = clearLine + 0.004f - 0.20f,
            outer = clearLine + 0.004f,
        )
        val dead = OrbitRules.step(
            World(arcs = listOf(hit, safe), score = 40, spawnIn = 10f),
            flip = false,
            dt = 0.02f,
        )
        assertFalse(dead.alive)
        assertEquals(40, dead.score)
        assertFalse(dead.arcs.any { it.cleared })

        val still = OrbitRules.step(dead, flip = true, dt = 0.2f)
        assertEquals(dead, still)
    }

    @Test
    fun anUnsteeredRunScoresOrDiesWithoutRaisingTheScoreOnImpact() {
        var world = World(rng = 42L)
        var steps = 0
        while (world.alive && steps < 3_000) {
            val before = world.score
            world = OrbitRules.step(world, flip = false, dt = 0.016f)
            if (world.alive) assertTrue(world.score >= before) else assertEquals(before, world.score)
            steps += 1
        }
        assertTrue(world.score > 0 || !world.alive)
    }

    @Test
    fun paceRisesAndTheSpawnGapShrinks() {
        assertEquals(0f, OrbitRules.pace(0), 0.0001f)
        assertTrue(OrbitRules.angularSpeed(0) < OrbitRules.angularSpeed(8))
        assertTrue(OrbitRules.angularSpeed(8) < OrbitRules.angularSpeed(40))
        assertTrue(OrbitRules.angularSpeed(400) < 2.81f)
        assertTrue(OrbitRules.inboundSpeed(0) < OrbitRules.inboundSpeed(30))
        assertTrue(OrbitRules.inboundSpeed(400) < 0.69f)
        assertTrue(OrbitRules.spawnInterval(0) > OrbitRules.spawnInterval(30))
        assertTrue(OrbitRules.spawnInterval(100) > 0.45f)
        assertTrue(OrbitRules.pace(10) > OrbitRules.pace(2))
        assertTrue(OrbitRules.pace(40) < 1f)
        assertTrue(OrbitRules.pace(500) <= 1f)
    }

    @Test
    fun aLongFrameIsClamped() {
        val next = OrbitRules.step(World(spawnIn = 10f), flip = false, dt = 100f)
        assertEquals(OrbitRules.MAX_FRAME_SECONDS, next.elapsed, 0.001f)
        assertTrue(next.alive)
    }

    @Test
    fun openingArcsStayClearOfTheShip() {
        listOf(1L, 2L, 99L, 123456789L, -4L).forEach { seed ->
            val next = OrbitRules.step(
                World(rng = seed, spawnIn = 0f, shipAngle = 0.4f, dodges = 0),
                flip = false,
                dt = 0.02f,
            )
            val arc = next.arcs.single()
            assertTrue(
                "seed $seed landed on the ship",
                OrbitRules.angularDistance(next.shipAngle, arc.center) >= 1.1f,
            )
        }
    }

    @Test
    fun aNewArcLeavesAGapBesideOneAlreadyFalling() {
        val blocker = Arc(1, center = 0.2f, halfSpan = 0.5f, inner = 1.4f, outer = 1.6f)
        val center = OrbitRules.placeCenter(
            shipAngle = 2f,
            dodges = 10,
            existing = listOf(blocker),
            unit = 0.2f / OrbitRules.TAU,
            halfSpan = 0.4f,
        )
        val need = 0.4f + blocker.halfSpan + OrbitRules.SHIP_ANGULAR_HALF * 2f + OrbitRules.GAP_MARGIN
        assertTrue(OrbitRules.angularDistance(center, blocker.center) >= need - 0.001f)
    }

    @Test
    fun randomUnitIsStableAndInsideTheUnitInterval() {
        val (state, unit) = OrbitRules.nextUnit(1L)
        val again = OrbitRules.nextUnit(1L)
        assertEquals(state, again.first)
        assertEquals(unit, again.second, 0f)
        assertTrue(unit in 0f..1f)
        val (recovered, _) = OrbitRules.nextUnit(0L)
        assertTrue(recovered != 0L)
    }

    @Test
    fun bestScoreIsStrictlyHigher() {
        assertFalse(OrbitRules.isNewBest(0, 0))
        assertFalse(OrbitRules.isNewBest(40, 40))
        assertTrue(OrbitRules.isNewBest(41, 40))
        assertEquals(40, OrbitRules.shownBest(stored = 40, run = 10))
        assertEquals(80, OrbitRules.shownBest(stored = 40, run = 80))
    }

    @Test
    fun namesMatchTheLeaderboardRule() {
        assertEquals("Geetha M", OrbitRules.canonicalName("  Geetha   M "))
        assertEquals("O'Neil", OrbitRules.canonicalName("O'Neil"))
        assertEquals("A", OrbitRules.canonicalName("A"))
        assertNull(OrbitRules.canonicalName("   "))
        assertNull(OrbitRules.canonicalName("..."))
        assertNull(OrbitRules.canonicalName("Geetha@M"))
        assertNull(OrbitRules.canonicalName("12345678901234567"))
        assertEquals("1234567890123456", OrbitRules.canonicalName("1234567890123456"))
    }
}
