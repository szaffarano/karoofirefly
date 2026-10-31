package io.github.derstrassi.karoofirefly.ble.igpsport

/**
 * iGPSport VS800 light protocol over the Nordic UART service.
 *
 * Reverse-engineered on a real VS800: same 20-byte header + Maxim CRC8 framing as other iGPSport
 * lights, but light control lives on service 105 and every sub-payload is wrapped in protobuf field 5.
 * Set/read current mode on sub 7, read supported modes on sub 6; the type-0x03 state frame on sub 7
 * carries mode (byte 7), remaining minutes (bytes 11-13) and battery percent (byte 15).
 */
object IgpsportProtocol {
    const val UART_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val UART_WRITE = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val UART_NOTIFY = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
    const val ADVERT_MARKER = "a238c112-8136-52a9-364b-d61ac015024e"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"

    const val HEADER_LEN = 20
    const val MAX_CHUNK = 20

    const val MODE_OFF = 0

    private const val TYPE_DATA = 0x01
    private const val TYPE_STATE = 0x03

    private const val SERVICE_LIGHT = 105
    private const val OP_WRITE = 1
    private const val OP_READ = 2

    private const val SUB_SUPPORTED = 6
    private const val SUB_CURRENT_MODE = 7

    private const val F_SERVICE = 1
    private const val F_OPERATE = 2
    private const val F_SUB = 3
    private const val F_PAYLOAD = 5
    private const val F_SUPPORTED_MODE = 1
    private const val F_CURRENT_MODE = 2

    // Header byte offsets.
    private const val IDX_TYPE = 0
    private const val IDX_SERVICE = 1
    private const val IDX_SUB = 2
    private const val IDX_OP = 4
    private const val IDX_RESERVED = 7
    private const val IDX_PAYLEN = 8
    private const val IDX_PAYCRC = 9
    private const val IDX_FLAG = 10
    private const val IDX_HEADCRC = 19

    // State-frame byte offsets.
    private const val IDX_STATE_MODE = 7
    private const val IDX_STATE_MIN0 = 11
    private const val IDX_STATE_MIN1 = 12
    private const val IDX_STATE_MIN2 = 13
    private const val IDX_STATE_BATT = 15

    private const val BYTE = 8
    private const val TWO_BYTES = 16
    private const val BYTE_MASK = 0xFF
    private const val MAX_PAYLOAD = 0xFF

    fun readSupportedModes(): ByteArray = message(SUB_SUPPORTED, OP_READ)

    fun readCurrentMode(): ByteArray = message(SUB_CURRENT_MODE, OP_READ)

    /**
     * Selects [mode] on the VS800. [MODE_OFF] turns the beam off (field 5 is sent with no mode value,
     * which the light reads as off) while keeping the light reachable.
     */
    fun setMode(mode: Int): ByteArray {
        val inner = if (mode == MODE_OFF) ByteArray(0) else ProtoWire.varintField(F_CURRENT_MODE, mode.toLong())
        return message(SUB_CURRENT_MODE, OP_WRITE, ProtoWire.messageField(F_PAYLOAD, inner))
    }

    fun chunks(
        frame: ByteArray,
        size: Int = MAX_CHUNK,
    ): List<ByteArray> = (frame.indices step size).map { frame.copyOfRange(it, minOf(it + size, frame.size)) }

    fun headerValid(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_LEN &&
            Crc8.maxim(bytes, 0, HEADER_LEN - 1) == (bytes[IDX_HEADCRC].toInt() and BYTE_MASK)

    /** Length of the frame starting at bytes[0]. Only data frames carry a payload. */
    fun expectedLength(bytes: ByteArray): Int =
        if (bytes.size >= HEADER_LEN && (bytes[IDX_TYPE].toInt() and BYTE_MASK) == TYPE_DATA) {
            HEADER_LEN + (bytes[IDX_PAYLEN].toInt() and BYTE_MASK)
        } else {
            HEADER_LEN
        }

    /** Parses a whole, validated frame into a [Vs800Update]; null if not understood. */
    fun parse(bytes: ByteArray): Vs800Update? {
        if (!headerValid(bytes) || (bytes[IDX_SERVICE].toInt() and BYTE_MASK) != SERVICE_LIGHT) return null
        return when (bytes[IDX_TYPE].toInt() and BYTE_MASK) {
            TYPE_STATE -> parseState(bytes)
            TYPE_DATA -> parseData(bytes)
            else -> null
        }
    }

    private fun parseState(bytes: ByteArray): Vs800Update? {
        if ((bytes[IDX_SUB].toInt() and BYTE_MASK) != SUB_CURRENT_MODE) return null
        val minutes =
            (bytes[IDX_STATE_MIN0].toInt() and BYTE_MASK) or
                ((bytes[IDX_STATE_MIN1].toInt() and BYTE_MASK) shl BYTE) or
                ((bytes[IDX_STATE_MIN2].toInt() and BYTE_MASK) shl TWO_BYTES)
        return Vs800Update(
            currentMode = bytes[IDX_STATE_MODE].toInt() and BYTE_MASK,
            remainingMinutes = minutes,
            batteryPercent = bytes[IDX_STATE_BATT].toInt() and BYTE_MASK,
        )
    }

    @Suppress("ReturnCount") // a frame parser is clearest with early bail-outs on malformed input
    private fun parseData(bytes: ByteArray): Vs800Update? {
        val len = bytes[IDX_PAYLEN].toInt() and BYTE_MASK
        if (bytes.size < HEADER_LEN + len) return null
        val payload = bytes.copyOfRange(HEADER_LEN, HEADER_LEN + len)
        if (Crc8.maxim(payload) != (bytes[IDX_PAYCRC].toInt() and BYTE_MASK)) return null
        val fields = ProtoWire.parse(payload) ?: return null
        val inner = fields.message(F_PAYLOAD)?.let { ProtoWire.parse(it) } ?: return Vs800Update()
        return when (bytes[IDX_SUB].toInt() and BYTE_MASK) {
            SUB_SUPPORTED -> Vs800Update(supportedModes = inner.varints(F_SUPPORTED_MODE).map { it.toInt() })
            SUB_CURRENT_MODE -> Vs800Update(currentMode = inner.varint(F_CURRENT_MODE)?.toInt())
            else -> Vs800Update()
        }
    }

    private fun message(
        sub: Int,
        op: Int,
        body: ByteArray = ByteArray(0),
    ): ByteArray {
        val payload =
            ProtoWire.varintField(F_SERVICE, SERVICE_LIGHT.toLong()) +
                ProtoWire.varintField(F_OPERATE, op.toLong()) +
                ProtoWire.varintField(F_SUB, sub.toLong()) +
                body
        require(payload.size <= MAX_PAYLOAD) { "payload too long: ${payload.size}" }
        val header = ByteArray(HEADER_LEN) { BYTE_MASK.toByte() }
        header[IDX_TYPE] = TYPE_DATA.toByte()
        header[IDX_SERVICE] = SERVICE_LIGHT.toByte()
        header[IDX_SUB] = sub.toByte()
        header[IDX_OP] = op.toByte()
        header[IDX_RESERVED] = 0
        header[IDX_PAYLEN] = payload.size.toByte()
        header[IDX_PAYCRC] = Crc8.maxim(payload).toByte()
        header[IDX_FLAG] = 1
        header[IDX_HEADCRC] = Crc8.maxim(header, 0, HEADER_LEN - 1).toByte()
        return header + payload
    }
}

/** Parsed VS800 report; non-null fields are merged into the controller's live state. */
data class Vs800Update(
    val currentMode: Int? = null,
    val supportedModes: List<Int>? = null,
    val batteryPercent: Int? = null,
    val remainingMinutes: Int? = null,
)
