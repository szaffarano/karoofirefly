package io.github.derstrassi.karoofirefly.ble.igpsport

import kotlinx.coroutines.flow.Flow

/** A connection to one VS800 that emits link events and accepts whole protocol frames. */
interface LightLink {
    fun connect(address: String): Flow<LinkEvent>

    fun send(frame: ByteArray): Boolean
}

sealed interface LinkEvent {
    data object Connected : LinkEvent

    data object Disconnected : LinkEvent

    class Fragment(
        val bytes: ByteArray,
    ) : LinkEvent
}
