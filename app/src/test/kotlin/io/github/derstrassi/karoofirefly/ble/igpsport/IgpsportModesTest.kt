package io.github.derstrassi.karoofirefly.ble.igpsport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IgpsportModesTest {
    @Test
    fun `mode id round-trips to protocol number`() {
        assertEquals(0, IgpsportModes.toProtocol("OFF"))
        assertEquals(1, IgpsportModes.toProtocol("MODE_1"))
        assertEquals(20, IgpsportModes.toProtocol("MODE_20"))
        assertEquals("OFF", IgpsportModes.id(0))
        assertEquals("MODE_7", IgpsportModes.id(7))
    }

    @Test
    fun `unknown mode id resolves to null`() {
        assertNull(IgpsportModes.toProtocol("BOGUS"))
        assertNull(IgpsportModes.toProtocol("MODE_x"))
    }

    @Test
    fun `options for a supported list keep known order with OFF first`() {
        val options = IgpsportModes.options(listOf(5, 1, 7))
        assertEquals(listOf("OFF", "MODE_1", "MODE_7", "MODE_5"), options.map { it.id })
        assertEquals("Off", options.first().displayName)
    }

    @Test
    fun `options fall back to the full known set when supported is unknown`() {
        val ids = IgpsportModes.options(null).map { it.id }
        assertEquals(listOf("OFF", "MODE_1", "MODE_2", "MODE_7", "MODE_4", "MODE_5", "MODE_20"), ids)
    }
}
