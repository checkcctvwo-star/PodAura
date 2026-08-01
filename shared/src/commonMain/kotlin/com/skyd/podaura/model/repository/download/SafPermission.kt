package com.skyd.podaura.model.repository.download

/**
 * Persist read/write access to the SAF tree [uri] across device reboots.
 *
 * FileKit's `rememberDirectoryPickerLauncher` does not call
 * `ContentResolver.takePersistableUriPermission`, so the access granted by the directory
 * picker is lost when the process dies (e.g. after a reboot). Call this in the picker's
 * on-result callback — while the temporary grant is still active — so the stored tree URI
 * remains usable by [writeTranscodedToSaf] later.
 *
 * No-op on non-Android targets.
 */
expect fun persistSafPermission(uri: String)
