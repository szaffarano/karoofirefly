package io.github.derstrassi.karoofirefly.ble.igpsport

/** Hex helpers for frame logging and tests. */
object Hex {
    private const val HEX_RADIX = 16
    private const val HEX_PAIR = 2

    fun decode(s: String): ByteArray =
        s
            .replace(" ", "")
            .chunked(HEX_PAIR)
            .map { it.toInt(HEX_RADIX).toByte() }
            .toByteArray()

    fun encode(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
