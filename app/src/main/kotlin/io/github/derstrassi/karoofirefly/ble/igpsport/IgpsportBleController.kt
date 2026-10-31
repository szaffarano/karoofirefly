package io.github.derstrassi.karoofirefly.ble.igpsport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.derstrassi.karoofirefly.DiscoveredLight
import io.github.derstrassi.karoofirefly.data.LightProtocol
import io.github.derstrassi.karoofirefly.light.LightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Controls iGPSport VS800 lights over the raw [GattLightLink]. Mirrors the surface
 * KarooLightControllerExtension already uses on the Magicshine controller.
 */
class IgpsportBleController(
    context: Context,
) : LightController {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Session(
        val link: LightLink,
        val job: Job,
        val assembler: FrameAssembler = FrameAssembler(),
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val names = ConcurrentHashMap<String, String>()
    private val connected = ConcurrentHashMap<String, Boolean>()
    private val supportedModes = ConcurrentHashMap<String, List<Int>>()
    private val batteryLevels = ConcurrentHashMap<String, Int>()

    private val _discoveredLights = MutableStateFlow<List<DiscoveredLight>>(emptyList())
    val discoveredLights: StateFlow<List<DiscoveredLight>> = _discoveredLights

    var onDeviceConnected: (() -> Unit)? = null
    var assignedDeviceIds: Set<String> = emptySet()

    private var scanCallback: ScanCallback? = null

    private val bleScanner
        get() =
            (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
                ?.bluetoothLeScanner

    fun supportedModes(deviceId: String): List<Int>? = supportedModes[deviceId]

    fun allConnected(deviceIds: Set<String>): Boolean = deviceIds.all { connected[it] == true }

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission") // guarded by hasScanPermission()
    @Suppress("ReturnCount") // guard clauses for scanner availability and permission
    fun startDiscovery() {
        if (scanCallback != null) return
        if (!hasScanPermission()) {
            Timber.w("$TAG: BLUETOOTH_SCAN not granted; skipping discovery")
            return
        }
        val scanner =
            bleScanner ?: run {
                Timber.w("$TAG: no BLE scanner")
                return
            }
        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val name = result.scanRecord?.deviceName
                    val address = result.device?.address
                    val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                    if (name == null || address == null || !ScanMatch.isVs800(name, uuids)) return
                    if (names.put(address, name) == null) {
                        Timber.d("$TAG: found VS800 %s (%s)", address, name)
                        updateDiscoveredLights()
                        if (address in assignedDeviceIds) connect(address)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Timber.e("$TAG: BLE scan failed: %d", errorCode)
                }
            }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(null, settings, callback) }
            .onSuccess { scanCallback = callback }
            .onFailure { Timber.w(it, "$TAG: could not start discovery") }
    }

    @SuppressLint("MissingPermission") // guarded by hasScanPermission()
    fun stopDiscovery() {
        val cb = scanCallback ?: return
        scanCallback = null
        if (!hasScanPermission()) return
        runCatching { bleScanner?.stopScan(cb) }
    }

    fun connect(address: String) {
        if (sessions.containsKey(address)) return
        names.putIfAbsent(address, DEFAULT_NAME)
        val link = GattLightLink(appContext)
        val job = scope.launch { link.connect(address).collect { handle(address, link, it) } }
        sessions[address] = Session(link, job)
    }

    private fun handle(
        address: String,
        link: LightLink,
        event: LinkEvent,
    ) {
        when (event) {
            LinkEvent.Connected -> {
                connected[address] = true
                updateDiscoveredLights()
                onDeviceConnected?.invoke()
                link.send(IgpsportProtocol.readSupportedModes())
                link.send(IgpsportProtocol.readCurrentMode())
            }

            LinkEvent.Disconnected -> {
                connected[address] = false
                sessions[address]?.assembler?.reset()
                updateDiscoveredLights()
            }

            is LinkEvent.Fragment -> {
                val frames = sessions[address]?.assembler?.push(event.bytes) ?: return
                var changed = false
                frames.forEach { frame ->
                    val update = IgpsportProtocol.parse(frame) ?: return@forEach
                    update.supportedModes?.let { supportedModes[address] = it }
                    update.batteryPercent?.let { batteryLevels[address] = it }
                    changed = true
                }
                if (changed) updateDiscoveredLights()
            }
        }
    }

    override fun setMode(
        deviceId: String,
        modeName: String,
    ) {
        val mode =
            IgpsportModes.toProtocol(modeName) ?: run {
                Timber.w("$TAG: unknown mode %s for %s", modeName, deviceId)
                return
            }
        val link =
            sessions[deviceId]?.link ?: run {
                Timber.w("$TAG: not connected to %s; cannot set mode", deviceId)
                return
            }
        Timber.d("$TAG: setMode(%s, %s) -> %d", deviceId, modeName, mode)
        link.send(IgpsportProtocol.setMode(mode))
    }

    fun disconnect(address: String) {
        sessions.remove(address)?.job?.cancel()
        connected[address] = false
        updateDiscoveredLights()
    }

    private fun updateDiscoveredLights() {
        _discoveredLights.value =
            names.map { (address, name) ->
                DiscoveredLight(
                    id = address,
                    name = name,
                    manufacturer = MANUFACTURER,
                    protocol = LightProtocol.IGPSPORT,
                    connected = connected[address] == true,
                    batteryPercent = batteryLevels[address],
                )
            }
    }

    fun destroy() {
        stopDiscovery()
        sessions.values.forEach { it.job.cancel() }
        sessions.clear()
        scope.cancel()
    }

    private companion object {
        const val TAG = "IgpsportBle"
        const val MANUFACTURER = "iGPSport"
        const val DEFAULT_NAME = "VS800"
    }
}
