package io.github.derstrassi.karoofirefly.engine

import io.github.derstrassi.karoofirefly.data.DayTimeZone
import io.github.derstrassi.karoofirefly.data.LightAssignment
import io.github.derstrassi.karoofirefly.data.LightControllerSettings
import io.github.derstrassi.karoofirefly.data.LightRole
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

    // TIME_BASED with a default TimeBasedController (no GPS) always resolves the natural zone to DAY.
    private fun timeBasedSettings() =
        LightControllerSettings(
            autoOnWithRide = true,
            autoOffOnPause = true,
            keepLightsOnAtNightWhilePaused = false,
            lightControlMode = "TIME_BASED",
            lightAssignments =
                listOf(
                    LightAssignment(
                        deviceId = "dev-1",
                        deviceName = "Light",
                        role = LightRole.FRONT,
                        dayMode = "MODE_1",
                        nightMode = "MODE_2",
                    ),
                ),
        )

    @Test
    fun `reapplyActiveZone is silent before any zone has been applied`() {
        val (engine, applied) = newEngine()

        engine.reapplyActiveZone()

        assertTrue("initial unset zone must not be replayed as OFF", applied.isEmpty())
    }

    @Test
    fun `per-device replay targets only the reconnected device`() {
        val (engine, _) = newEngine()
        val perDevice = mutableListOf<Pair<DayTimeZone?, String>>()
        engine.onApplyZoneToDevice = { zone, id -> perDevice.add(zone to id) }
        engine.settings = timeBasedSettings()

        engine.reapplyActiveZone("dev-1")
        assertTrue("must stay silent before any zone applied", perDevice.isEmpty())

        engine.onRideStart() // applies DAY
        engine.reapplyActiveZone("dev-1")

        assertEquals(listOf<Pair<DayTimeZone?, String>>(DayTimeZone.DAY to "dev-1"), perDevice)
    }

    @Test
    fun `auto ride resumes lights after an auto-off pause`() {
        val (engine, applied) = newEngine()
        engine.settings = timeBasedSettings()

        engine.onRideStart() // AUTO_CONTROL -> DAY
        engine.onRidePause() // auto-off -> OFF
        assertEquals(null, engine.activeZone.value)
        applied.clear()

        engine.onRideStart() // resume

        assertEquals(DayTimeZone.DAY, engine.activeZone.value)
        assertTrue("resume must re-apply the running zone", applied.contains(DayTimeZone.DAY))
    }

    @Test
    fun `manual override ride resumes lights after an auto-off pause`() {
        val (engine, applied) = newEngine()
        engine.settings = timeBasedSettings()

        engine.onRideStart() // AUTO_CONTROL -> DAY
        engine.onCycleMode() // manual override DAY -> NIGHT
        assertEquals(DayTimeZone.NIGHT, engine.activeZone.value)
        engine.onRidePause() // auto-off -> OFF
        assertEquals(null, engine.activeZone.value)
        applied.clear()

        engine.onRideStart() // resume

        assertEquals(
            "resume must restore the manual override zone, not stay off",
            DayTimeZone.NIGHT,
            engine.activeZone.value,
        )
        assertTrue("resume must re-apply the manual zone", applied.contains(DayTimeZone.NIGHT))
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
