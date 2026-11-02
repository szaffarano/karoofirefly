package io.github.derstrassi.karoofirefly.ble.igpsport

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc8Test {
    @Test
    fun `standard CRC-8 MAXIM check value`() {
        assertEquals(0xA1, Crc8.maxim("123456789".toByteArray()))
    }

    @Test
    fun `matches captured VS800 supported-modes frame`() {
        val frame =
            Hex.decode(
                "01 69 06 FF 02 FF FF 00 14 E2 01 FF FF FF FF FF FF FF FF 08 " +
                    "08 69 10 02 18 06 2A 0C 08 01 08 02 08 07 08 04 08 05 08 14",
            )
        assertEquals(0xE2, Crc8.maxim(frame, IgpsportProtocol.HEADER_LEN, frame.size)) // payload CRC (byte 9)
        assertEquals(0x08, Crc8.maxim(frame, 0, IgpsportProtocol.HEADER_LEN - 1)) // header CRC (byte 19)
    }
}
