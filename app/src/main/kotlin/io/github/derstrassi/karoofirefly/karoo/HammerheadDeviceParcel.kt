package io.github.derstrassi.karoofirefly.karoo

import android.os.Parcel
import android.os.Parcelable

/**
 * Hand-written stand-in for Hammerhead's `Device` parcel, used only when the real class cannot
 * be loaded from the sensor-service APK (newer Karoo firmware denies third-party read access to
 * it via SELinux). Only [writeToParcel] is ever used.
 *
 * The byte layout reproduces `Device(uid, DeviceInfo(name = "NA"))` with every optional field at
 * its default, taken from sensorservice 4.217.1. It is therefore frozen to that `Device` layout:
 * if Hammerhead changes the class, these bytes stop matching and must be regenerated. On firmware
 * where the real class is still readable the reflection path in [KarooLightControl] is preferred,
 * which adapts to whatever layout that firmware ships; this parcel is the fallback for firmware
 * that blocks the APK read.
 */
internal class HammerheadDeviceParcel(
    private val uid: String,
) : Parcelable {
    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(uid)
        // info: DeviceInfo(name = "NA"), written via writeParcelable (class name + payload)
        dest.writeString("io.hammerhead.datamodels.timeseriesData.models.DeviceInfo")
        dest.writeString("NA") // name
        dest.writeString(null) // axsBikeId
        dest.writeInt(0) // syncedWithAxs
        dest.writeInt(0) // nameEditedByUser
        dest.writeInt(0) // lastBatteryValue == null
        dest.writeInt(0) // manufacturerInfo == null
        dest.writeInt(0) // componentInfo == null
        dest.writeList(emptyList<String>()) // supportedDataTypes
        dest.writeList(emptyList<String>()) // blockedDataTypes
        dest.writeInt(1) // isEnabled
        dest.writeInt(1) // isConnectedAtLeastOnce
        dest.writeInt(2) // connectionType ordinal: OTHER
        dest.writeParcelable(null, 0) // wheelCircumferenceInfo
        dest.writeParcelable(null, 0) // shiftingInfo
        dest.writeInt(-1) // lightMode
        dest.writeInt(-1) // autoLightControl
        dest.writeInt(-1) // ctfOffset
        dest.writeInt(-1) // priority
        dest.writeInt(-1) // functionSets == null
        dest.writeString(null) // axsPrimarySerial
        dest.writeInt(0) // alerts: size
        dest.writeIntArray(IntArray(0)) // alerts: ordinals
        dest.writeInt(-1) // tirePressureAlerts
    }

    override fun describeContents(): Int = 0

    companion object {
        // Present only to satisfy the Parcelable contract. This parcel is write-only: it is never
        // read back on our side, so deserialization is unsupported.
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<HammerheadDeviceParcel> {
                override fun createFromParcel(source: Parcel): HammerheadDeviceParcel =
                    throw UnsupportedOperationException("HammerheadDeviceParcel is write-only")

                override fun newArray(size: Int): Array<HammerheadDeviceParcel?> = arrayOfNulls(size)
            }
    }
}
