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

/**
 * Derives the lights' desired zone from two orthogonal inputs — the ride [phase] and the control
 * [intent] — via a single pure [desiredZone] computation. Every external event just updates an
 * input and calls [reconcile]; there is no snapshot/restore and no event-ordering state, so pause,
 * resume, repeated events and mid-paused startup all fall out of the same reduction.
 *
 * Precedence inside [desiredZone]:
 *  1. a paused ride with auto-off (and not kept on at night) → OFF, regardless of intent;
 *  2. a manual override → its target (survives pause, beats idle/auto);
 *  3. idle → OFF when auto-off-with-ride, otherwise hold the current output;
 *  4. riding under auto → the natural zone (unless manual-only / auto-on disabled, or debug forces).
 */
class LightControlEngine(
    private val timeController: TimeBasedController,
    private val ambientLightSensor: AmbientLightSensor? = null,
) {
    companion object {
        const val ZONE_CHECK_INTERVAL_MS = 30_000L
    }

    /** Public, UI-facing state derived from [phase] + [intent]. */
    enum class EngineState {
        IDLE,
        AUTO_CONTROL,
        MANUAL_OVERRIDE,
        PAUSED,
    }

    private enum class RidePhase { IDLE, RIDING, PAUSED }

    private sealed interface Intent {
        data object Auto : Intent

        /** A manual pick; [target] null means OFF. [anchor] is the natural zone when it was set, so
         * the override auto-expires once the natural zone drifts away from it. */
        data class Manual(
            val target: DayTimeZone?,
            val anchor: DayTimeZone,
        ) : Intent
    }

    var onApplyZone: ((DayTimeZone?) -> Unit)? = null

    var onApplyZoneToDevice: ((DayTimeZone?, String) -> Unit)? = null

    var onZoneChange: ((DayTimeZone, DayTimeZone, String) -> Unit)? = null

    private val zoneLock = Any()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var zoneCheckJob: Job? = null
    private var sensorObserveJob: Job? = null

    private var phase = RidePhase.IDLE
    private var intent: Intent = Intent.Auto
    private var debugActive = false

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
        phase = RidePhase.RIDING
        reconcile()
        if (settings.controlMode != LightControlMode.MANUAL_ONLY) startZoneChecking()
    }

    fun onRidePause() {
        Timber.d("LightControlEngine: ride paused")
        phase = RidePhase.PAUSED
        reconcile()
    }

    fun onRideStop() {
        Timber.d("LightControlEngine: ride stopped")
        stopZoneChecking()
        phase = RidePhase.IDLE
        intent = Intent.Auto // a stop clears any manual override
        reconcile()
    }

    fun onToggleLights() {
        val natural = determineCurrentZone()
        val target = if (_activeZone.value != null) null else resolveOnZone()
        intent = Intent.Manual(target, natural)
        reconcile()
    }

    fun onCycleMode() {
        val natural = determineCurrentZone()
        val dayHasModes = hasModesForZone(DayTimeZone.DAY)
        val nightHasModes = hasModesForZone(DayTimeZone.NIGHT)
        val target =
            when (_activeZone.value) {
                null -> {
                    if (dayHasModes) {
                        DayTimeZone.DAY
                    } else if (nightHasModes) {
                        DayTimeZone.NIGHT
                    } else {
                        DayTimeZone.DAY
                    }
                }

                DayTimeZone.DAY -> {
                    if (nightHasModes) DayTimeZone.NIGHT else null
                }

                DayTimeZone.NIGHT -> {
                    null
                }
            }
        intent = Intent.Manual(target, natural)
        reconcile()
    }

    fun setDebugMode(enabled: Boolean) {
        Timber.d("LightControlEngine: debug mode ${if (enabled) "ON" else "OFF"}")
        debugActive = enabled
        intent = Intent.Auto
        if (enabled) {
            phase = RidePhase.RIDING
            reconcile()
            startZoneChecking()
        } else {
            stopZoneChecking()
            phase = RidePhase.IDLE
            reconcile()
        }
    }

    fun setDebugZone(zone: DayTimeZone) {
        Timber.d("LightControlEngine: debug set zone ${zone.name}")
        intent = Intent.Manual(zone, determineCurrentZone())
        reconcile()
    }

    /**
     * Re-applies the current desired zone to every assigned light so a controller that just
     * (re)connected is restored to the engine's state. Stays silent until a zone has been applied at
     * least once. Shares [zoneLock] with [applyZone] so the read and dispatch are atomic against a
     * concurrent reconcile.
     */
    fun reapplyActiveZone() {
        synchronized(zoneLock) {
            if (hasEverAppliedZone) onApplyZone?.invoke(_activeZone.value)
        }
    }

    /**
     * Re-applies the current desired zone to a single reconnected device, leaving every other light
     * untouched (so a reconnect cannot clobber an unrelated light that is, e.g., radar-flashing).
     */
    fun reapplyActiveZone(deviceId: String) {
        synchronized(zoneLock) {
            if (hasEverAppliedZone) onApplyZoneToDevice?.invoke(_activeZone.value, deviceId)
        }
    }

    /**
     * The single reducer: expire a drifted override, publish the derived [EngineState], apply the
     * freshly computed desired zone, and fire a zone-change notification on a natural transition
     * while actively auto-controlling.
     */
    private fun reconcile() {
        val natural = determineCurrentZone()

        (intent as? Intent.Manual)?.let { manual ->
            if (natural != manual.anchor) {
                Timber.d("Override anchor ${manual.anchor} drifted to $natural; resuming auto control")
                intent = Intent.Auto
            }
        }
        publishState()

        val previousZone = _currentZone.value
        _currentZone.value = natural

        applyZone(desiredZone(natural))

        if (natural != previousZone && phase == RidePhase.RIDING && intent is Intent.Auto) {
            Timber.d("Zone: $previousZone → $natural")
            onZoneChange?.invoke(previousZone, natural, zoneChangeReason(natural))
        }
    }

    @Suppress("ReturnCount") // ordered precedence guards read most clearly as early returns
    private fun desiredZone(natural: DayTimeZone): DayTimeZone? {
        if (phase == RidePhase.PAUSED && settings.autoOffOnPause && !keepLightsOnAtNight()) return null

        (intent as? Intent.Manual)?.let { return it.target }

        if (phase == RidePhase.IDLE) {
            return if (settings.autoOffWithRide) null else _activeZone.value
        }

        // Riding (or paused but kept on) under auto control.
        if (!debugActive) {
            if (settings.controlMode == LightControlMode.MANUAL_ONLY) return null
            if (!settings.autoOnWithRide) return null
        }
        return natural
    }

    private fun publishState() {
        // A pause shows PAUSED even over a manual override; otherwise an active manual override shows
        // (even while idle, e.g. toggled before a ride); then riding is auto; otherwise idle.
        _state.value =
            when {
                phase == RidePhase.PAUSED -> EngineState.PAUSED
                intent is Intent.Manual -> EngineState.MANUAL_OVERRIDE
                phase == RidePhase.RIDING -> EngineState.AUTO_CONTROL
                else -> EngineState.IDLE
            }
    }

    private fun zoneChangeReason(zone: DayTimeZone): String =
        when (settings.controlMode) {
            LightControlMode.TIME_BASED -> {
                "Sunrise/Sunset"
            }

            LightControlMode.AMBIENT_LIGHT -> {
                "Light sensor"
            }

            LightControlMode.COMBINED -> {
                if (zone !=
                    timeController.getCurrentZone()
                ) {
                    "Light sensor"
                } else {
                    "Sunrise/Sunset"
                }
            }

            LightControlMode.MANUAL_ONLY -> {
                "Manual"
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

    private fun keepLightsOnAtNight(): Boolean =
        settings.keepLightsOnAtNightWhilePaused &&
            determineCurrentZone() == DayTimeZone.NIGHT

    private fun applyZone(zone: DayTimeZone?) {
        synchronized(zoneLock) {
            if (hasEverAppliedZone && zone == _activeZone.value) return // no change; avoid redundant dispatch
            hasEverAppliedZone = true
            _activeZone.value = zone
            onApplyZone?.invoke(zone)
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
                    if (phase == RidePhase.RIDING) reconcile()
                }
            }
    }

    private fun stopSensorObserving() {
        sensorObserveJob?.cancel()
        sensorObserveJob = null
    }

    private fun startZoneChecking() {
        zoneCheckJob?.cancel()
        zoneCheckJob =
            scope.launch {
                while (true) {
                    delay(ZONE_CHECK_INTERVAL_MS)
                    if (phase == RidePhase.RIDING) reconcile()
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
