package io.github.derstrassi.karoofirefly.ble.igpsport

/** Decides whether a scanned BLE device is an iGPSport VS800 we can control. */
object ScanMatch {
    private val MODEL_NAME = Regex("^VS800.*", RegexOption.IGNORE_CASE)

    fun isVs800(
        name: String?,
        serviceUuids: List<String>,
    ): Boolean {
        // The light also advertises a second identity "<model>_U"; that is not the control endpoint.
        if (name == null || name.endsWith("_U", ignoreCase = true)) return false
        val markerMatch =
            name.contains("VS800", ignoreCase = true) &&
                serviceUuids.any { it.equals(IgpsportProtocol.ADVERT_MARKER, ignoreCase = true) }
        return markerMatch || MODEL_NAME.matches(name)
    }
}
