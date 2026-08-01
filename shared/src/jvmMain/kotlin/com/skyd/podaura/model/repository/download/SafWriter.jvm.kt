package com.skyd.podaura.model.repository.download

import io.github.vinceglb.filekit.PlatformFile

actual fun writeTranscodedToSaf(
    tempFile: PlatformFile,
    rootTreeUri: String,
    showName: String,
    fileName: String,
): String {
    throw UnsupportedOperationException("SAF write is only supported on Android")
}
