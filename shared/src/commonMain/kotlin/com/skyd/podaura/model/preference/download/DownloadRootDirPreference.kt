package com.skyd.podaura.model.preference.download

import androidx.datastore.preferences.core.stringPreferencesKey
import com.skyd.ksp.annotation.Preference
import com.skyd.podaura.model.preference.BasePreference

@Preference
object DownloadRootDirPreference : BasePreference<String>() {
    private const val DOWNLOAD_ROOT_DIR = "downloadRootDir"

    override val default = ""
    override val key = stringPreferencesKey(DOWNLOAD_ROOT_DIR)
}
