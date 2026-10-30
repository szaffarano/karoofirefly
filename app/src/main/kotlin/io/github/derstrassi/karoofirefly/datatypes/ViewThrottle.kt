package io.github.derstrassi.karoofirefly.datatypes

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

// karoo-ext drops ViewEmitter.updateView calls faster than 1Hz. Space our updates slightly above
// that window so the most recent value is never throttled away.
internal const val VIEW_MIN_INTERVAL_MS = 1100L

/**
 * Collect a flow that drives a RemoteViews update, as a trailing rate limiter: emits are
 * de-duplicated and conflated to the latest value, and [block] is spaced at least
 * [VIEW_MIN_INTERVAL_MS] apart. This guarantees the newest state is always rendered (within one
 * interval) without exceeding the karoo-ext 1Hz updateView throttle, even under rapid bursts.
 */
internal suspend fun <T> Flow<T>.collectThrottledView(block: (T) -> Unit) {
    distinctUntilChanged().conflate().collect { value ->
        block(value)
        delay(VIEW_MIN_INTERVAL_MS)
    }
}
