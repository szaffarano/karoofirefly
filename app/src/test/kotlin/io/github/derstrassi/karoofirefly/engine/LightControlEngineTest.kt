package io.github.derstrassi.karoofirefly.engine

import io.github.derstrassi.karoofirefly.data.DayTimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightControlEngineTest {
    private fun newEngine(): Pair<LightControlEngine, MutableList<DayTimeZone?>> {
        val engine = LightControlEngine(TimeBasedController())
        val applied = mutableListOf<DayTimeZone?>()
        engine.onApplyZone = { applied.add(it) }
        return engine to applied
    }

    @Test
    fun `reapplyActiveZone is silent before any zone has been applied`() {
        val (engine, applied) = newEngine()

        engine.reapplyActiveZone()

        assertTrue("initial unset zone must not be replayed as OFF", applied.isEmpty())
    }

    @Test
    fun `reapplyActiveZone replays the current on-zone`() {
        val (engine, applied) = newEngine()
        engine.onToggleLights()
        val onZone = engine.activeZone.value
        applied.clear()

        engine.reapplyActiveZone()

        assertEquals(listOf(onZone), applied)
    }

    @Test
    fun `reapplyActiveZone replays OFF once a zone has been applied`() {
        val (engine, applied) = newEngine()
        engine.onToggleLights() // turn on
        engine.onToggleLights() // turn off -> applyZone(null)
        assertEquals(null, engine.activeZone.value)
        applied.clear()

        engine.reapplyActiveZone()

        assertEquals(listOf<DayTimeZone?>(null), applied)
    }
}
