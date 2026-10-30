package io.github.derstrassi.karoofirefly.datatypes

import android.content.Context
import android.widget.RemoteViews
import io.github.derstrassi.karoofirefly.DiscoveredLight
import io.github.derstrassi.karoofirefly.R
import io.github.derstrassi.karoofirefly.data.LightRole
import io.github.derstrassi.karoofirefly.engine.LightControlEngine
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Read-only ride-screen data field showing the battery level of each configured light.
 *
 * BLE lights report an exact percentage; ANT+ lights report a coarse status word
 * (GOOD/OK/LOW/CRITICAL), so each protocol is displayed in its native granularity to avoid implying
 * false precision. Disconnected/unknown lights show a placeholder. One row per assigned light.
 */
class LightBatteryDataType(
    private val engine: LightControlEngine,
    private val discoveredLights: StateFlow<List<DiscoveredLight>>,
) : DataTypeImpl("karoo-light-controller", TYPE_ID) {
    companion object {
        const val TYPE_ID = "light-battery"
        private const val PLACEHOLDER = "\u2014"

        private fun batteryValue(light: DiscoveredLight?): String =
            when {
                light == null || !light.connected -> PLACEHOLDER
                light.batteryStatus != null -> light.batteryStatus
                light.batteryPercent != null -> "${light.batteryPercent}%"
                else -> PLACEHOLDER
            }
    }

    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        if (config.preview) {
            val preview = RemoteViews(context.packageName, R.layout.light_battery_view)
            preview.setTextViewText(R.id.light_battery_text, "F: 80%\nR: GOOD")
            emitter.updateView(preview)
            emitter.setCancellable { }
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch {
            combine(discoveredLights, engine.settingsFlow) { lights, settings ->
                Pair(lights, settings.lightAssignments)
            }.collectThrottledView { (lights, assignments) ->
                val text =
                    if (assignments.isEmpty()) {
                        "No lights"
                    } else {
                        assignments.joinToString("\n") { assignment ->
                            val role = if (assignment.role == LightRole.FRONT) "F" else "R"
                            val light = lights.find { it.id == assignment.deviceId }
                            "$role: ${batteryValue(light)}"
                        }
                    }

                val remoteViews = RemoteViews(context.packageName, R.layout.light_battery_view)
                remoteViews.setTextViewText(R.id.light_battery_text, text)
                emitter.updateView(remoteViews)
            }
        }

        emitter.setCancellable { scope.cancel() }
    }
}
