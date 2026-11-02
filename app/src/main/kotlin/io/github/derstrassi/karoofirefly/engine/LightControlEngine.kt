package io.github.derstrassi.karoofirefly.engine

import io.github.derstrassi.karoofirefly.data.DayTimeZone
import io.github.derstrassi.karoofirefly.data.LightControlMode
import io.github.derstrassi.karoofirefly.data.LightControllerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class LightControlEngine(
    private val timeController: TimeBasedController,
    private val ambientLightSensor: AmbientLightSensor? = null,
) {
    companion object {
        const val ZONE_CHECK_INTERVAL_MS = 30_000L
    }

    enum class EngineState {
        IDLE,
        AUTO_CONTROL,
        MANUAL_OVERRIDE,
        PAUSED,
    }

    var onApplyZone: ((DayTimeZone?) -> Unit)? = null

    var onApplyZoneToDevice: ((DayTimeZone?, String) -> Unit)? = null

    var onZoneChange: ((DayTimeZone, DayTimeZone, String) -> Unit)? = null

    private val zoneLock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var zoneCheckJob: Job? = null
    private var sensorObserveJob: Job? = null

    private var overrideZone: DayTimeZone? = null

    // Captured on pause so a resume can restore the exact pre-pause state, including a manual
    // override whose zone applyAutoMode would otherwise refuse to re-apply.
    private var stateBeforePause: EngineState? = null
    private var zoneBeforePause: DayTimeZone? = null

    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state

    private val _currentZone = MutableStateFlow(DayTimeZone.DAY)
    val currentZone: StateFlow<DayTimeZone> = _currentZone

    private val _activeZone = MutableStateFlow<DayTimeZone?>(null)
    val activeZone: StateFlow<DayTimeZone?> = _activeZone

    @Volatile
    var hasEverAppliedZone = false
        private set

    private val _settingsFlow = MutableStateFlow(LightControllerSettings())
    val settingsFlow: StateFlow<LightControllerSettings> = _settingsFlow

    @Volatile
    var settings: LightControllerSettings = LightControllerSettings()
        set(value) {
            field = value
            _settingsFlow.value = value
        }

    fun onRideStart() {
        Timber.d("LightControlEngine: ride started")
        if (settings.controlMode == LightControlMode.MANUAL_ONLY) return
        if (_state.value == EngineState.PAUSED && resumeFromPause()) {
            return
        }
        if (settings.autoOnWithRide) {
            _state.value = if (overrideZone != null) EngineState.MANUAL_OVERRIDE else EngineState.AUTO_CONTROL
            applyAutoMode()
            startZoneChecking()
        }
    }

    fun onRidePause() {
        Timber.d("LightControlEngine: ride paused")
        // Snapshot only on the transition into PAUSED; a repeated Paused notification must not
        // overwrite the real pre-pause state with PAUSED and the already-cleared zone.
        if (_state.value != EngineState.PAUSED) {
            stateBeforePause = _state.value
            zoneBeforePause = _activeZone.value
        }
        _state.value = EngineState.PAUSED
        if (settings.autoOffOnPause && !keepLightsOnAtNight()) {
            applyZone(null)
        }
    }

    /**
     * Restores the pre-pause light state on resume. Returns true when it handled the resume (the
     * engine was actively controlling lights before the pause); returns false when it was not (e.g.
     * the extension started mid-paused-ride and captured IDLE), so the caller falls through to
     * normal ride-start handling.
     */
    private fun resumeFromPause(): Boolean {
        Timber.d("LightControlEngine: ride resumed (was $stateBeforePause)")
        val restored =
            when (stateBeforePause) {
                EngineState.MANUAL_OVERRIDE -> {
                    _state.value = EngineState.MANUAL_OVERRIDE
                    zoneBeforePause?.let { applyZone(it) } // restore the manual pick pause turned off
                    applyAutoMode() // cancels the override only if the natural zone drifted while paused
                    startZoneChecking()
                    true
                }

                EngineState.AUTO_CONTROL -> {
                    _state.value = EngineState.AUTO_CONTROL
                    applyAutoMode() // activeZone is null after an auto-off pause, so this re-applies
                    startZoneChecking()
                    true
                }

                else -> {
                    // Wasn't actively controlling lights before the pause; reset to idle and let
                    // normal ride-start handling take over.
                    _state.value = EngineState.IDLE
                    false
                }
            }
        stateBeforePause = null
        zoneBeforePause = null
        return restored
    }

    private fun keepLightsOnAtNight(): Boolean =
        settings.keepLightsOnAtNightWhilePaused &&
            determineCurrentZone() == DayTimeZone.NIGHT

    fun onRideStop() {
        Timber.d("LightControlEngine: ride stopped")
        stopZoneChecking()
        overrideZone = null
        _state.value = EngineState.IDLE
        if (settings.autoOffWithRide) {
            applyZone(null)
        }
    }

    fun onToggleLights() {
        _state.value = EngineState.MANUAL_OVERRIDE
        overrideZone = determineCurrentZone()
        if (_activeZone.value != null) {
            applyZone(null)
        } else {
            val zone = resolveOnZone()
            applyZone(zone)
        }
    }

    private fun resolveOnZone(): DayTimeZone {
        val zone = determineCurrentZone()
        if (hasModesForZone(zone)) return zone
        val otherZone = if (zone == DayTimeZone.DAY) DayTimeZone.NIGHT else DayTimeZone.DAY
        if (hasModesForZone(otherZone)) return otherZone
        return DayTimeZone.DAY
    }

    private fun hasModesForZone(zone: DayTimeZone): Boolean =
        settings.lightAssignments.any {
            it.modeForZone(zone) != "OFF"
        }

    fun onCycleMode() {
        _state.value = EngineState.MANUAL_OVERRIDE
        overrideZone = determineCurrentZone()

        val dayHasModes = hasModesForZone(DayTimeZone.DAY)
        val nightHasModes = hasModesForZone(DayTimeZone.NIGHT)

        when (_activeZone.value) {
            null -> {
                if (dayHasModes) {
                    applyZone(DayTimeZone.DAY)
                } else if (nightHasModes) {
                    applyZone(DayTimeZone.NIGHT)
                } else {
                    applyZone(DayTimeZone.DAY)
                }
            }

            DayTimeZone.DAY -> {
                if (nightHasModes) {
                    applyZone(DayTimeZone.NIGHT)
                } else {
                    applyZone(null)
                }
            }

            DayTimeZone.NIGHT -> {
                applyZone(null)
            }
        }
    }

    private fun applyAutoMode() {
        val zone = determineCurrentZone()

        if (_state.value == EngineState.MANUAL_OVERRIDE) {
            if (zone != overrideZone) {
                Timber.d("Zone changed during override ($overrideZone → $zone), resuming auto control")
                overrideZone = null
                _state.value = EngineState.AUTO_CONTROL
            } else {
                return
            }
        }

        val previousZone = _currentZone.value
        if (zone == previousZone && _activeZone.value != null) return

        _currentZone.value = zone
        applyZone(zone)

        if (zone != previousZone) {
            val reason =
                when (settings.controlMode) {
                    LightControlMode.TIME_BASED -> {
                        "Sunrise/Sunset"
                    }

                    LightControlMode.AMBIENT_LIGHT -> {
                        "Light sensor"
                    }

                    LightControlMode.COMBINED -> {
                        val timeZone = timeController.getCurrentZone()
                        if (zone != timeZone) "Light sensor" else "Sunrise/Sunset"
                    }

                    LightControlMode.MANUAL_ONLY -> {
                        "Manual"
                    }
                }
            Timber.d("Zone: $previousZone → $zone ($reason)")
            onZoneChange?.invoke(previousZone, zone, reason)
        }
    }

    private fun applyZone(zone: DayTimeZone?) {
        synchronized(zoneLock) {
            hasEverAppliedZone = true
            _activeZone.value = zone
            onApplyZone?.invoke(zone)
        }
    }

    /**
     * Re-applies the current desired zone (including a null zone, which means OFF) so a light that
     * reconnects after missing a command is restored to the engine's state. Stays silent until the
     * engine has applied a zone at least once, so an initial unset zone is not treated as OFF.
     *
     * Shares [zoneLock] with [applyZone] so the read of the current zone and its dispatch are atomic
     * with respect to a concurrent zone change; otherwise a stale replay could be dispatched after a
     * newer zone, leaving the light in the wrong state.
     */
    fun reapplyActiveZone() {
        synchronized(zoneLock) {
            if (hasEverAppliedZone) onApplyZone?.invoke(_activeZone.value)
        }
    }

    /**
     * Re-applies the current desired zone to a single reconnected device, leaving every other light
     * untouched (so a reconnect cannot clobber an unrelated light that is, e.g., radar-flashing).
     * Serialized with [applyZone] via [zoneLock] for the same atomicity reason as [reapplyActiveZone].
     */
    fun reapplyActiveZone(deviceId: String) {
        synchronized(zoneLock) {
            if (hasEverAppliedZone) onApplyZoneToDevice?.invoke(_activeZone.value, deviceId)
        }
    }

    private fun determineCurrentZone(): DayTimeZone =
        when (settings.controlMode) {
            LightControlMode.MANUAL_ONLY -> {
                DayTimeZone.DAY
            }

            LightControlMode.TIME_BASED -> {
                timeController.getCurrentZone()
            }

            LightControlMode.AMBIENT_LIGHT -> {
                ambientLightSensor?.currentLightZone?.value ?: timeController.getCurrentZone()
            }

            LightControlMode.COMBINED -> {
                val timeZone = timeController.getCurrentZone()
                val sensorZone = ambientLightSensor?.currentLightZone?.value ?: timeZone
                if (sensorZone.ordinal > timeZone.ordinal) sensorZone else timeZone
            }
        }

    fun updateAmbientSensor() {
        ambientLightSensor?.let {
            if (settings.controlMode == LightControlMode.AMBIENT_LIGHT ||
                settings.controlMode == LightControlMode.COMBINED
            ) {
                it.nightThreshold = settings.ambientNightThreshold
                it.start()
                startSensorObserving()
            } else {
                it.stop()
                stopSensorObserving()
            }
        }
    }

    private fun startSensorObserving() {
        if (sensorObserveJob != null) return
        sensorObserveJob =
            scope.launch {
                ambientLightSensor?.currentLightZone?.collectLatest {
                    if (_state.value == EngineState.AUTO_CONTROL || _state.value == EngineState.MANUAL_OVERRIDE) {
                        applyAutoMode()
                    }
                }
            }
    }

    private fun stopSensorObserving() {
        sensorObserveJob?.cancel()
        sensorObserveJob = null
    }

    fun setDebugMode(enabled: Boolean) {
        if (enabled) {
            Timber.d("LightControlEngine: debug mode ON")
            _state.value = EngineState.AUTO_CONTROL
            applyAutoMode()
            startZoneChecking()
        } else {
            Timber.d("LightControlEngine: debug mode OFF")
            stopZoneChecking()
            _state.value = EngineState.IDLE
            applyZone(null)
        }
    }

    fun setDebugZone(zone: DayTimeZone) {
        Timber.d("LightControlEngine: debug set zone ${zone.name}")
        applyZone(zone)
    }

    private fun startZoneChecking() {
        zoneCheckJob?.cancel()
        zoneCheckJob =
            scope.launch {
                while (true) {
                    delay(ZONE_CHECK_INTERVAL_MS)
                    if (_state.value == EngineState.AUTO_CONTROL || _state.value == EngineState.MANUAL_OVERRIDE) {
                        applyAutoMode()
                    }
                }
            }
    }

    private fun stopZoneChecking() {
        zoneCheckJob?.cancel()
        zoneCheckJob = null
    }

    fun destroy() {
        zoneCheckJob?.cancel()
        sensorObserveJob?.cancel()
        ambientLightSensor?.stop()
        scope.cancel()
    }
}
