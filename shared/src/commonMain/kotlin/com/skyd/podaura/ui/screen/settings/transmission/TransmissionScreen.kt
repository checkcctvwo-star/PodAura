package com.skyd.podaura.ui.screen.settings.transmission

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation3.runtime.NavKey
import com.skyd.compone.component.BackIcon
import com.skyd.compone.component.ComponeScaffold
import com.skyd.compone.component.ComponeTopBar
import com.skyd.compone.component.ComponeTopBarStyle
import com.skyd.compone.component.DefaultBackClick
import com.skyd.compone.component.menu.CheckableListMenu
import com.skyd.compone.component.pointerOnBack
import com.skyd.podaura.model.preference.download.AutoTranscodeMp3Preference
import com.skyd.podaura.model.preference.download.DownloadNamingTemplatePreference
import com.skyd.podaura.model.preference.download.DownloadRootDirPreference
import com.skyd.podaura.model.preference.download.KeepOriginalAfterTranscodePreference
import com.skyd.podaura.model.preference.download.TranscodeBitratePreference
import com.skyd.settings.BaseSettingsItem
import com.skyd.settings.SettingsLazyColumn
import com.skyd.settings.SwitchSettingsItem
import com.skyd.settings.suspendString
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import podaura.shared.generated.resources.Res
import podaura.shared.generated.resources.auto_transcode_mp3
import podaura.shared.generated.resources.download_root_dir
import podaura.shared.generated.resources.keep_original_after_transcode
import podaura.shared.generated.resources.naming_template
import podaura.shared.generated.resources.naming_template_date_title
import podaura.shared.generated.resources.naming_template_show_number_title
import podaura.shared.generated.resources.naming_template_title_show
import podaura.shared.generated.resources.not_set
import podaura.shared.generated.resources.transcode_bitrate
import podaura.shared.generated.resources.transmission_screen_config_category
import podaura.shared.generated.resources.transmission_screen_name


@Serializable
data object TransmissionRoute : NavKey

@Composable
fun TransmissionScreen(
    onBack: (() -> Unit)? = DefaultBackClick,
    windowInsets: WindowInsets = WindowInsets.safeDrawing
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    var bitrateExpanded by rememberSaveable { mutableStateOf(false) }
    var templateExpanded by rememberSaveable { mutableStateOf(false) }

    val directoryPickerLauncher = rememberDirectoryPickerLauncher { dir ->
        if (dir != null) {
            // Store the SAF tree URI string (content://...). PlatformFile(storedValue) can
            // reconstruct it on Android, which writeTranscodedToSaf relies on.
            DownloadRootDirPreference.put(scope, dir.path)
        }
    }

    ComponeScaffold(
        modifier = Modifier.pointerOnBack(onBack = onBack),
        topBar = {
            ComponeTopBar(
                style = ComponeTopBarStyle.LargeFlexible,
                scrollBehavior = scrollBehavior,
                title = { Text(text = stringResource(Res.string.transmission_screen_name)) },
                navigationIcon = { if (onBack != null) BackIcon(onClick = onBack) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        },
        contentWindowInsets = windowInsets
    ) { innerPadding ->
        SettingsLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding,
        ) {
            group(text = { getString(Res.string.transmission_screen_config_category) }) {
                item {
                    val rootDir = DownloadRootDirPreference.current
                    val notSetText = stringResource(Res.string.not_set)
                    BaseSettingsItem(
                        icon = null,
                        text = stringResource(Res.string.download_root_dir),
                        descriptionText = rootDir.ifBlank { notSetText },
                        onClick = { directoryPickerLauncher.launch() },
                    )
                }
                item {
                    SwitchSettingsItem(
                        imageVector = null,
                        text = stringResource(Res.string.auto_transcode_mp3),
                        checked = AutoTranscodeMp3Preference.current,
                        onCheckedChange = { AutoTranscodeMp3Preference.put(scope, it) },
                    )
                }
                item {
                    BaseSettingsItem(
                        icon = null,
                        text = stringResource(Res.string.transcode_bitrate),
                        descriptionText = "${TranscodeBitratePreference.current} kbps",
                        extraContent = {
                            BitrateMenu(
                                expanded = bitrateExpanded,
                                onDismissRequest = { bitrateExpanded = false },
                            )
                        },
                        onClick = { bitrateExpanded = true },
                    )
                }
                item {
                    BaseSettingsItem(
                        icon = null,
                        text = stringResource(Res.string.naming_template),
                        descriptionText = suspendString(DownloadNamingTemplatePreference.current) {
                            namingTemplateDisplayName(it)
                        },
                        extraContent = {
                            NamingTemplateMenu(
                                expanded = templateExpanded,
                                onDismissRequest = { templateExpanded = false },
                            )
                        },
                        onClick = { templateExpanded = true },
                    )
                }
                item {
                    SwitchSettingsItem(
                        imageVector = null,
                        text = stringResource(Res.string.keep_original_after_transcode),
                        checked = KeepOriginalAfterTranscodePreference.current,
                        onCheckedChange = { KeepOriginalAfterTranscodePreference.put(scope, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BitrateMenu(expanded: Boolean, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val bitrate = TranscodeBitratePreference.current

    CheckableListMenu(
        expanded = expanded,
        current = bitrate,
        values = TranscodeBitratePreference.values,
        displayName = { "${it} kbps" },
        onChecked = { TranscodeBitratePreference.put(scope, it) },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun NamingTemplateMenu(expanded: Boolean, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val template = DownloadNamingTemplatePreference.current

    CheckableListMenu(
        expanded = expanded,
        current = template,
        values = DownloadNamingTemplatePreference.values,
        displayName = { namingTemplateDisplayName(it) },
        onChecked = { DownloadNamingTemplatePreference.put(scope, it) },
        onDismissRequest = onDismissRequest,
    )
}

private suspend fun namingTemplateDisplayName(template: String): String = when (template) {
    DownloadNamingTemplatePreference.TITLE_SHOW ->
        getString(Res.string.naming_template_title_show)

    DownloadNamingTemplatePreference.SHOW_NUMBER_TITLE ->
        getString(Res.string.naming_template_show_number_title)

    DownloadNamingTemplatePreference.DATE_TITLE ->
        getString(Res.string.naming_template_date_title)

    else -> template
}