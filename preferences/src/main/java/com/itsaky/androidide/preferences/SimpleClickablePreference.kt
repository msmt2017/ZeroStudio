package com.itsaky.androidide.preferences

import android.os.Parcel
import android.os.Parcelable
import androidx.preference.Preference

/**
 * A simple preference which is expected to be clickable only.
 *
 * Author: Akash Yadav
 */
class SimpleClickablePreference(
    override val key: String,
    override val title: Int,
    override val summary: Int? = null,
    override val icon: Int? = null,
    private val onClick: ((Preference) -> Unit)? = null // 使用函数类型
) : SimplePreference(), Parcelable {

    override fun onPreferenceClick(preference: Preference): Boolean {
        onClick?.invoke(preference)
        return true
    }

    // Parcelable implementation
    constructor(parcel: Parcel) : this(
        key = parcel.readString() ?: "",
        title = parcel.readInt(),
        summary = parcel.readValue(Int::class.java.classLoader) as? Int,
        icon = parcel.readValue(Int::class.java.classLoader) as? Int,
        onClick = null // Functions cannot be serialized
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(key)
        parcel.writeInt(title)
        parcel.writeValue(summary)
        parcel.writeValue(icon)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SimpleClickablePreference> {
        override fun createFromParcel(parcel: Parcel): SimpleClickablePreference {
            return SimpleClickablePreference(parcel)
        }

        override fun newArray(size: Int): Array<SimpleClickablePreference?> {
            return arrayOfNulls(size)
        }
    }
}