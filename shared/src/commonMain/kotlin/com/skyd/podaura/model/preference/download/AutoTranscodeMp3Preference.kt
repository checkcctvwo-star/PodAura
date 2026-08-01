package com.skyd.podaura.model.preference.download

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.skyd.ksp.annotation.Preference
import com.skyd.podaura.model.preference.BasePreference

@Preference
object AutoTranscodeMp3Preference : BasePreference<Boolean>() {
    private const val AUTO_TRANSCODE_MP3 = "autoTranscodeMp3"

    override val default = true
    override val key = booleanPreferencesKey(AUTO_TRANSCODE_MP3)
}
