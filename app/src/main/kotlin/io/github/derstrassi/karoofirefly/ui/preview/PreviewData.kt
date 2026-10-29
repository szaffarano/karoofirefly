package io.github.derstrassi.karoofirefly.ui.preview

import io.github.derstrassi.karoofirefly.DiscoveredLight
import io.github.derstrassi.karoofirefly.data.LightAssignment
import io.github.derstrassi.karoofirefly.data.LightControlMode
import io.github.derstrassi.karoofirefly.data.LightControllerSettings
import io.github.derstrassi.karoofirefly.data.LightProtocol
import io.github.derstrassi.karoofirefly.data.LightRole
import java.util.Calendar

/**
 * Shared, realistic sample values for @Preview composables. Keeping them here (instead of
 * re-typing literals in every preview) keeps previews consistent and easy to tweak.
 *
 * This is tooling-only data: it is never referenced by production code paths.
 */
object PreviewData {
    val frontAntLight =
        DiscoveredLight(
            id = "RTL515",
            name = "Varia RTL515",
            manufacturer = "Garmin",
            protocol = LightProtocol.ANT_PLUS,
            connected = true,
            batteryPercent = 82,
        )

    val rearBleLight =
        DiscoveredLight(
            id = "VS800",
            name = "SEEMEE 300",
            manufacturer = "Magicshine",
            protocol = LightProtocol.BLE,
            connected = true,
            batteryPercent = 45,
        )

    val disconnectedLight =
        DiscoveredLight(
            id = "M1",
            name = "Magicshine M1",
            manufacturer = "Magicshine",
            protocol = LightProtocol.BLE,
            connected = false,
            batteryPercent = null,
        )

    val frontAntAssignment =
        LightAssignment(
            deviceId = frontAntLight.id,
            deviceName = frontAntLight.name,
            role = LightRole.FRONT,
            protocol = LightProtocol.ANT_PLUS,
            dayMode = "FAST_FLASH",
            nightMode = "STEADY4",
            radarWarnFlash = true,
        )

    val rearBleAssignment =
        LightAssignment(
            deviceId = rearBleLight.id,
            deviceName = rearBleLight.name,
            role = LightRole.REAR,
            protocol = LightProtocol.BLE,
            dayMode = "FLASH_50",
            nightMode = "STEADY_100",
        )

    val discoveredLights = listOf(frontAntLight, rearBleLight, disconnectedLight)

    val populatedSettings =
        LightControllerSettings(
            dawnOffsetMinutes = -15,
            duskOffsetMinutes = 30,
            lightControlMode = LightControlMode.COMBINED.name,
            ambientNightThreshold = 50,
            lightAssignments = listOf(frontAntAssignment, rearBleAssignment),
        )

    val emptySettings = LightControllerSettings()

    val sunrise: Calendar = calendarAt(hour = 6, minute = 42)

    val sunset: Calendar = calendarAt(hour = 19, minute = 58)

    private fun calendarAt(
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
}
