package io.github.derstrassi.karoofirefly.ble.igpsport

import io.github.derstrassi.karoofirefly.data.LightModeOption
import io.github.derstrassi.karoofirefly.data.LightModeProvider

/**
 * Maps the VS800's protocol mode numbers to the opaque mode-id strings KarooFireFly persists and the
 * rider-facing labels. Labels are provisional (confirm on hardware); the id strings are a stable
 * contract once released.
 */
object IgpsportModes {
    const val OFF_ID = "OFF"
    private const val PREFIX = "MODE_"

    /**
     * Known, usable modes in display order (steady brightest-first, then flashes). Mode 20 is in the
     * light's supported list but produces no visible output on the VS800, so it is deliberately not
     * offered.
     */
    private val LABELS =
        linkedMapOf(
            1 to "High",
            2 to "Mid",
            7 to "Low",
            4 to "Flash A",
            5 to "Flash B",
        )

    fun id(mode: Int): String = if (mode == IgpsportProtocol.MODE_OFF) OFF_ID else "$PREFIX$mode"

    fun toProtocol(id: String): Int? =
        if (id == OFF_ID) IgpsportProtocol.MODE_OFF else id.removePrefix(PREFIX).toIntOrNull()

    fun label(mode: Int): String = LABELS[mode] ?: "Mode $mode"

    /**
     * Options for a light: OFF first, then the labelled modes the light supports, in display order.
     * Only known-usable modes are offered; unlabelled modes the light reports (e.g. the no-op mode 20)
     * are left out. Falls back to the full known set when the supported list is unknown.
     */
    fun options(supported: List<Int>?): List<LightModeOption> {
        val modes = LABELS.keys.filter { supported == null || it in supported }
        return listOf(LightModeOption(OFF_ID, "Off")) + modes.map { LightModeOption(id(it), label(it)) }
    }
}

/** Exposes the VS800's modes to the settings UI; [supported] is the list read from the light, if known. */
class IgpsportModeProvider(
    private val supported: List<Int>?,
) : LightModeProvider {
    override fun availableModes(): List<LightModeOption> = IgpsportModes.options(supported)
}
