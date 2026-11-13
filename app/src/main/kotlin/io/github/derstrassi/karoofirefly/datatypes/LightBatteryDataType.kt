package io.github.derstrassi.karoofirefly.datatypes

import android.content.Context
import android.widget.RemoteViews
import io.github.derstrassi.karoofirefly.DiscoveredLight
import io.github.derstrassi.karoofirefly.R
import io.github.derstrassi.karoofirefly.data.LightProtocol
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
 * BLE lights report an exact percentage; ANT+ lights report only a coarse status enum, which is
 * mapped to a representative percentage (in KarooLightControllerExtension) so both protocols are
 * shown consistently as a percentage. Disconnected/unknown lights show a placeholder. One row per
 * assigned light, labelled with its role prefix (F/R) and a descriptor: for ANT+ the manufacturer
 * (its device name is a generic serial) and for BLE the device name (its real model), each falling
 * back to the other and finally to the role alone when neither is available.
 */
class LightBatteryDataType(
    private val engine: LightControlEngine,
    private val discoveredLights: StateFlow<List<DiscoveredLight>>,
) : DataTypeImpl("karoo-light-controller", TYPE_ID) {
    companion object {
        const val TYPE_ID = "light-battery"
        private const val PLACEHOLDER = "\u2014"

        /**
         * Picks the most meaningful descriptor for a light: ANT+ devices carry a generic
         * "light <serial>" name but a clean manufacturer, while BLE devices carry a real model
         * name. Prefer whichever is better per protocol, fall back to the other, then null.
         */
        private fun descriptor(light: DiscoveredLight?): String? {
            val name = light?.name?.takeIf { it.isNotBlank() }
            val manufacturer = light?.manufacturer?.takeIf { it.isNotBlank() }
            return when (light?.protocol) {
                LightProtocol.ANT_PLUS -> manufacturer ?: name
                else -> name ?: manufacturer
            }
        }

        private fun batteryValue(light: DiscoveredLight?): String =
            when {
                light == null || !light.connected -> PLACEHOLDER
                light.batteryPercent != null -> "${light.batteryPercent}%"
                else -> PLACEHOLDER
            }
    }

    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        // Show Karoo's native field header (the "Light Battery" name) as a heading above the rows.
        emitter.onNext(UpdateGraphicConfig(showHeader = true))

        if (config.preview) {
            val preview = RemoteViews(context.packageName, R.layout.light_battery_view)
            preview.setTextViewText(R.id.light_battery_text, "F VS800: 80%\nR Garmin: 50%")
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
                            val descriptor = descriptor(light)
                            val label = if (descriptor != null) "$role $descriptor" else role
                            "$label: ${batteryValue(light)}"
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
