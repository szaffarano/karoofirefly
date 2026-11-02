package io.github.derstrassi.karoofirefly.ble.igpsport

/** CRC-8/MAXIM (Dallas 1-Wire): reflected polynomial 0x8C, init 0x00, no final XOR. */
object Crc8 {
    private const val POLY = 0x8C
    private const val BITS = 8
    private const val BYTE_MASK = 0xFF

    fun maxim(
        data: ByteArray,
        from: Int = 0,
        to: Int = data.size,
    ): Int {
        var crc = 0
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and BYTE_MASK)
            repeat(BITS) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor POLY else crc ushr 1
            }
        }
        return crc
    }
}
