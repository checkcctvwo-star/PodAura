package com.skyd.podaura.model.preference.download

import androidx.datastore.preferences.core.intPreferencesKey
import com.skyd.ksp.annotation.Preference
import com.skyd.podaura.model.preference.BasePreference

@Preference
object TranscodeBitratePreference : BasePreference<Int>() {
    private const val TRANSCODE_BITRATE = "transcodeBitrate"

    const val BITRATE_64 = 64
    const val BITRATE_96 = 96
    const val BITRATE_128 = 128
    const val BITRATE_192 = 192
    val values = listOf(BITRATE_64, BITRATE_96, BITRATE_128, BITRATE_192)

    override val default = BITRATE_128
    override val key = intPreferencesKey(TRANSCODE_BITRATE)
}
