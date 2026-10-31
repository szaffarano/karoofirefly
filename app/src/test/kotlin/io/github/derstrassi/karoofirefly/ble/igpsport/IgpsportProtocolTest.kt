package io.github.derstrassi.karoofirefly.ble.igpsport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgpsportProtocolTest {
    @Test
    fun `setMode builds a service-105 sub-7 write with field5 field2 = mode`() {
        val f = IgpsportProtocol.setMode(1)
        assertEquals(0x01, f[0].toInt() and 0xFF) // TYPE_DATA
        assertEquals(105, f[1].toInt() and 0xFF) // service
        assertEquals(7, f[2].toInt() and 0xFF) // sub = current mode
        assertEquals(1, f[4].toInt() and 0xFF) // OP_WRITE
        assertTrue(IgpsportProtocol.headerValid(f))

        val fields = ProtoWire.parse(f.copyOfRange(IgpsportProtocol.HEADER_LEN, f.size))!!
        val inner = ProtoWire.parse(fields.message(5)!!)!!
        assertEquals(1L, inner.varint(2))
    }

    @Test
    fun `setMode OFF sends field5 present but with no mode value`() {
        val f = IgpsportProtocol.setMode(IgpsportProtocol.MODE_OFF)
        val fields = ProtoWire.parse(f.copyOfRange(IgpsportProtocol.HEADER_LEN, f.size))!!
        val field5 = fields.message(5)
        assertTrue("field5 wrapper must be present for OFF", field5 != null)
        assertNull("OFF carries no mode value", ProtoWire.parse(field5!!)!!.varint(2))
    }

    @Test
    fun `read frames target the right subs and op`() {
        assertEquals(6, IgpsportProtocol.readSupportedModes()[2].toInt() and 0xFF)
        assertEquals(7, IgpsportProtocol.readCurrentMode()[2].toInt() and 0xFF)
        assertEquals(2, IgpsportProtocol.readCurrentMode()[4].toInt() and 0xFF) // OP_READ
    }

    @Test
    fun `parses supported modes reply`() {
        val frame =
            Hex.decode(
                "01 69 06 FF 02 FF FF 00 14 E2 01 FF FF FF FF FF FF FF FF 08 " +
                    "08 69 10 02 18 06 2A 0C 08 01 08 02 08 07 08 04 08 05 08 14",
            )
        assertEquals(listOf(1, 2, 7, 4, 5, 20), IgpsportProtocol.parse(frame)!!.supportedModes)
    }

    @Test
    fun `parses current mode reply`() {
        val frame =
            Hex.decode(
                "01 69 07 FF 02 FF FF 00 0A 99 01 FF FF FF FF FF FF FF FF 60 " +
                    "08 69 10 02 18 07 2A 02 10 02",
            )
        assertEquals(2, IgpsportProtocol.parse(frame)!!.currentMode)
    }

    @Test
    fun `parses state frame mode battery and minutes`() {
        val frame = Hex.decode("03 69 07 FF 01 FF FF 02 FF FF FF E1 00 00 00 5A FF FF FF B3")
        val u = IgpsportProtocol.parse(frame)!!
        assertEquals(2, u.currentMode)
        assertEquals(90, u.batteryPercent)
        assertEquals(225, u.remainingMinutes)
    }

    @Test
    fun `rejects a frame with a corrupt header CRC`() {
        val frame = Hex.decode("03 69 07 FF 01 FF FF 02 FF FF FF E1 00 00 00 5A FF FF FF 00")
        assertNull(IgpsportProtocol.parse(frame))
    }
}
