package io.github.derstrassi.karoofirefly.datatypes

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.derstrassi.karoofirefly.R
import io.github.derstrassi.karoofirefly.data.DayTimeZone
import io.github.derstrassi.karoofirefly.engine.LightControlEngine
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Interactive ride-screen data field that cycles the active light zone (Day -> Night -> Off) on tap.
 *
 * karoo-ext (<=1.1.9) has no inbound tap [io.hammerhead.karooext.models.ViewEvent], so the tap is
 * captured via a [PendingIntent] attached to the RemoteViews root (validated on Karoo 3). Tapping
 * triggers [LightControlEngine.onCycleMode], so the field is an alternative entry point to the
 * existing `cycle-mode` BonusAction and honours the Manual > Auto > Ride priority.
 */
class ModeSwitchDataType(
    private val engine: LightControlEngine,
) : DataTypeImpl("karoo-light-controller", TYPE_ID) {
    companion object {
        const val TYPE_ID = "mode-switch"

        // karoo-ext drops updateView calls faster than 1Hz; debounce collapses the burst of
        // engine state changes on ride start/pause so the final (meaningful) value is the one shown.
        private const val VIEW_DEBOUNCE_MS = 250L
        private const val ACTION_CYCLE = "io.github.derstrassi.karoofirefly.MODE_SWITCH_CYCLE"
        private const val REQUEST_CODE = 4212

        private fun zoneLabel(zone: DayTimeZone?): String =
            when (zone) {
                DayTimeZone.DAY -> "Day"
                DayTimeZone.NIGHT -> "Night"
                null -> "Off"
            }
    }

    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        if (config.preview) {
            val preview = RemoteViews(context.packageName, R.layout.mode_switch_view)
            preview.setTextViewText(R.id.mode_switch_zone, zoneLabel(DayTimeZone.DAY))
            preview.setTextViewText(R.id.mode_switch_state, "")
            emitter.updateView(preview)
            emitter.setCancellable { }
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    Timber.d("ModeSwitchDataType: tap -> onCycleMode")
                    engine.onCycleMode()
                }
            }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(ACTION_CYCLE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val pendingIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                REQUEST_CODE,
                Intent(ACTION_CYCLE).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        scope.launch {
            combine(engine.activeZone, engine.state) { zone, state ->
                Pair(zone, state)
            }.debounce(VIEW_DEBOUNCE_MS).distinctUntilChanged().collect { (zone, state) ->
                val remoteViews = RemoteViews(context.packageName, R.layout.mode_switch_view)
                remoteViews.setTextViewText(R.id.mode_switch_zone, zoneLabel(zone))
                remoteViews.setTextViewText(
                    R.id.mode_switch_state,
                    if (state == LightControlEngine.EngineState.MANUAL_OVERRIDE) "manual" else "",
                )
                remoteViews.setOnClickPendingIntent(R.id.mode_switch_root, pendingIntent)
                emitter.updateView(remoteViews)
            }
        }

        emitter.setCancellable {
            runCatching { context.applicationContext.unregisterReceiver(receiver) }
            scope.cancel()
        }
    }
}
