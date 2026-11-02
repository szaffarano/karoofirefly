package io.github.derstrassi.karoofirefly.ble.igpsport

/** Reassembles the light's notification fragments (at most 20 bytes each) into whole frames. */
class FrameAssembler {
    private var buffer = ByteArray(0)

    fun push(fragment: ByteArray): List<ByteArray> {
        buffer += fragment
        if (buffer.size > MAX_BUFFER) {
            buffer = ByteArray(0)
            return emptyList()
        }
        val frames = mutableListOf<ByteArray>()
        while (buffer.size >= IgpsportProtocol.HEADER_LEN) {
            if (!IgpsportProtocol.headerValid(buffer)) {
                buffer = buffer.copyOfRange(1, buffer.size) // resync one byte at a time
            } else {
                val need = IgpsportProtocol.expectedLength(buffer)
                if (buffer.size < need) break // wait for the rest of this frame
                frames += buffer.copyOfRange(0, need)
                buffer = buffer.copyOfRange(need, buffer.size)
            }
        }
        return frames
    }

    fun reset() {
        buffer = ByteArray(0)
    }

    private companion object {
        const val MAX_BUFFER = 1024
    }
}
