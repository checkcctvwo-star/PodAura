package com.skyd.podaura.model.preference.download

import androidx.datastore.preferences.core.stringPreferencesKey
import com.skyd.ksp.annotation.Preference
import com.skyd.podaura.model.preference.BasePreference

@Preference
object DownloadNamingTemplatePreference : BasePreference<String>() {
    private const val DOWNLOAD_NAMING_TEMPLATE = "downloadNamingTemplate"

    const val TITLE_SHOW = "TitleAndShow"
    const val SHOW_NUMBER_TITLE = "ShowNumberTitle"
    const val DATE_TITLE = "DateAndTitle"
    val values = listOf(TITLE_SHOW, SHOW_NUMBER_TITLE, DATE_TITLE)

    override val default = TITLE_SHOW
    override val key = stringPreferencesKey(DOWNLOAD_NAMING_TEMPLATE)
}
