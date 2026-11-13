package io.hammerhead.datamodels.timeseriesData.models

import android.os.Parcel
import android.os.Parcelable

/**
 * Stand-in for Hammerhead's `LightMode` enum, used when the real class cannot be loaded from
 * the sensor-service APK (newer Karoo firmware denies third-party read access to it via SELinux).
 *
 * A [android.os.Bundle] serializes an object's class *name* followed by its parcel payload, and
 * the receiver re-resolves that name with its own class loader. Hammerhead's `LightMode` is a
 * Parcelize enum whose parcel form is simply `writeString(name())`, so emitting the mode name
 * under this identically-named class makes the sensor service reconstruct a real enum on its side.
 *
 * The fully-qualified class name must stay exactly as it is — that string is the contract.
 */
class LightMode(
    private val modeName: String,
) : Parcelable {
    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(modeName)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<LightMode> {
                override fun createFromParcel(source: Parcel): LightMode = LightMode(source.readString() ?: "UNKNOWN")

                override fun newArray(size: Int): Array<LightMode?> = arrayOfNulls(size)
            }
    }
}
