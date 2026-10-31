package io.github.derstrassi.karoofirefly.ble.igpsport

import java.io.ByteArrayOutputStream

/** Minimal protobuf wire format: varint (type 0) and length-delimited (type 2) only. */
object ProtoWire {
    sealed interface Value {
        data class Varint(
            val value: Long,
        ) : Value

        class Bytes(
            val bytes: ByteArray,
        ) : Value
    }

    private const val CONTINUATION = 0x80
    private const val PAYLOAD_MASK = 0x7F
    private const val SHIFT = 7
    private const val TAG_SHIFT = 3
    private const val WIRE_LEN_DELIMITED = 2L
    private const val WIRE_TYPE_MASK = 7L
    private const val BYTE_MASK = 0xFF

    fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            if (v and PAYLOAD_MASK.toLong().inv() == 0L) {
                out.write(v.toInt())
                return out.toByteArray()
            }
            out.write(((v and PAYLOAD_MASK.toLong()) or CONTINUATION.toLong()).toInt())
            v = v ushr SHIFT
        }
    }

    fun varintField(
        field: Int,
        value: Long,
    ): ByteArray = varint(field.toLong() shl TAG_SHIFT) + varint(value)

    fun messageField(
        field: Int,
        inner: ByteArray,
    ): ByteArray =
        varint((field.toLong() shl TAG_SHIFT) or WIRE_LEN_DELIMITED) +
            varint(inner.size.toLong()) +
            inner

    /** One message level -> field number to values, repeated fields in order; null if malformed. */
    @Suppress("ReturnCount") // a wire-format parser is clearest with early bail-outs on malformed input
    fun parse(bytes: ByteArray): Map<Int, List<Value>>? {
        val result = LinkedHashMap<Int, MutableList<Value>>()
        var pos = 0

        fun readVarint(): Long? {
            var shift = 0
            var acc = 0L
            while (pos < bytes.size) {
                val b = bytes[pos++].toInt() and BYTE_MASK
                acc = acc or ((b and PAYLOAD_MASK).toLong() shl shift)
                if (b and CONTINUATION == 0) return acc
                shift += SHIFT
            }
            return null
        }

        while (pos < bytes.size) {
            val key = readVarint() ?: return null
            val field = (key ushr TAG_SHIFT).toInt()
            when ((key and WIRE_TYPE_MASK).toInt()) {
                0 -> {
                    val value = readVarint() ?: return null
                    result.getOrPut(field) { mutableListOf() }.add(Value.Varint(value))
                }

                WIRE_LEN_DELIMITED.toInt() -> {
                    // Compared as a Long against the bytes left: an Int cast or pos + len could wrap.
                    val rawLen = readVarint() ?: return null
                    if (rawLen < 0 || rawLen > bytes.size - pos) return null
                    val len = rawLen.toInt()
                    result
                        .getOrPut(field) { mutableListOf() }
                        .add(Value.Bytes(bytes.copyOfRange(pos, pos + len)))
                    pos += len
                }

                else -> {
                    return null
                }
            }
        }
        return result
    }
}

fun Map<Int, List<ProtoWire.Value>>.varint(field: Int): Long? =
    (this[field]?.firstOrNull() as? ProtoWire.Value.Varint)?.value

fun Map<Int, List<ProtoWire.Value>>.varints(field: Int): List<Long> =
    this[field].orEmpty().filterIsInstance<ProtoWire.Value.Varint>().map { it.value }

fun Map<Int, List<ProtoWire.Value>>.message(field: Int): ByteArray? =
    (this[field]?.firstOrNull() as? ProtoWire.Value.Bytes)?.bytes
