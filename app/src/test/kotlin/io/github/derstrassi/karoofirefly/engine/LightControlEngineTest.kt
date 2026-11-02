package io.github.derstrassi.karoofirefly.engine

import io.github.derstrassi.karoofirefly.data.DayTimeZone
import io.github.derstrassi.karoofirefly.data.LightAssignment
import io.github.derstrassi.karoofirefly.data.LightControllerSettings
import io.github.derstrassi.karoofirefly.data.LightRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightControlEngineTest {
    // A TimeBasedController whose natural zone the test controls directly.
    private class FakeTime(
        var zone: DayTimeZone = DayTimeZone.DAY,
    ) : TimeBasedController() {
        override fun getCurrentZone(): DayTimeZone = zone
    }

    private val time = FakeTime()

    private fun newEngine(): Pair<LightControlEngine, MutableList<DayTimeZone?>> {
        val engine = LightControlEngine(time)
        val applied = mutableListOf<DayTimeZone?>()
        engine.onApplyZone = { applied.add(it) }
        return engine to applied
    }

    private fun timeBasedSettings(
        autoOnWithRide: Boolean = true,
        autoOffWithRide: Boolean = true,
        autoOffOnPause: Boolean = true,
        keepLightsOnAtNightWhilePaused: Boolean = false,
    ) = LightControllerSettings(
        autoOnWithRide = autoOnWithRide,
        autoOffWithRide = autoOffWithRide,
        autoOffOnPause = autoOffOnPause,
        keepLightsOnAtNightWhilePaused = keepLightsOnAtNightWhilePaused,
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
    fun `toggle turns lights on while idle`() {
        val (engine, _) = newEngine()
        engine.settings = timeBasedSettings()

        engine.onToggleLights()

        assertEquals(DayTimeZone.DAY, engine.activeZone.value)
        assertEquals(LightControlEngine.EngineState.MANUAL_OVERRIDE, engine.state.value)
    }

    @Test
    fun `ride stop turns lights off when autoOffWithRide is enabled`() {
        val (engine, _) = newEngine()
        engine.settings = timeBasedSettings(autoOffWithRide = true)
        engine.onRideStart()
        assertEquals(DayTimeZone.DAY, engine.activeZone.value)

        engine.onRideStop()

        assertEquals(null, engine.activeZone.value)
        assertEquals(LightControlEngine.EngineState.IDLE, engine.state.value)
    }

    @Test
    fun `ride stop keeps lights on when autoOffWithRide is disabled`() {
        val (engine, _) = newEngine()
        engine.settings = timeBasedSettings(autoOffWithRide = false)
        engine.onRideStart()
        assertEquals(DayTimeZone.DAY, engine.activeZone.value)

        engine.onRideStop()

        assertEquals(DayTimeZone.DAY, engine.activeZone.value)
    }

    @Test
    fun `manual override drift-cancels to auto when the natural zone changes`() {
        val (engine, _) = newEngine()
        engine.settings = timeBasedSettings()
        engine.onRideStart() // auto DAY
        engine.onCycleMode() // manual override DAY -> NIGHT, anchored at DAY
        assertEquals(DayTimeZone.NIGHT, engine.activeZone.value)

        time.zone = DayTimeZone.NIGHT // natural zone drifts to night
        engine.onRidePause()
        engine.onRideStart() // resume re-evaluates

        assertEquals(DayTimeZone.NIGHT, engine.activeZone.value)
        assertEquals(LightControlEngine.EngineState.AUTO_CONTROL, engine.state.value)
    }

    @Test
    fun `ride start while already paused falls through to normal auto start`() {
        // Extension starts mid-paused-ride: state is IDLE, then a Paused event arrives first.
        val (engine, applied) = newEngine()
        engine.settings = timeBasedSettings()

        engine.onRidePause() // captures IDLE, turns off
        applied.clear()

        engine.onRideStart() // resume must not restore IDLE; it should auto-start

        assertEquals(DayTimeZone.DAY, engine.activeZone.value)
        assertTrue("startup-during-pause must still auto-start", applied.contains(DayTimeZone.DAY))
    }

    @Test
    fun `repeated pause notifications preserve the original pre-pause state`() {
        val (engine, applied) = newEngine()
        engine.settings = timeBasedSettings()

        engine.onRideStart() // AUTO_CONTROL -> DAY
        engine.onRidePause() // snapshot AUTO/DAY, off
        engine.onRidePause() // repeated pause must not clobber the snapshot
        applied.clear()

        engine.onRideStart() // resume

        assertEquals(DayTimeZone.DAY, engine.activeZone.value)
        assertTrue("repeated pause must still resume lights", applied.contains(DayTimeZone.DAY))
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
