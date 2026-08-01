package com.skyd.podaura.model.preference.download

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.skyd.ksp.annotation.Preference
import com.skyd.podaura.model.preference.BasePreference

@Preference
object KeepOriginalAfterTranscodePreference : BasePreference<Boolean>() {
    private const val KEEP_ORIGINAL_AFTER_TRANSCODE = "keepOriginalAfterTranscode"

    override val default = false
    override val key = booleanPreferencesKey(KEEP_ORIGINAL_AFTER_TRANSCODE)
}
