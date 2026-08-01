package com.skyd.podaura.model.repository.download

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import kotlinx.io.buffered

actual fun writeTranscodedToSaf(
    tempFile: PlatformFile,
    rootTreeUri: String,
    showName: String,
    fileName: String,
): String {
    val root = PlatformFile(rootTreeUri)
    val showDir = root div showName
    if (!showDir.exists()) showDir.createDirectories()

    // Resolve a non-colliding target name: "name (2).ext", "name (3).ext", ...
    var target = showDir div fileName
    if (target.exists()) {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        var counter = 2
        do {
            target = showDir div "$baseName ($counter)$extension"
            counter++
        } while (target.exists())
    }

    // Stream the transcoded temp file into the SAF target. On a UriWrapper, sink() creates
    // the document via DocumentsContract.createDocument when it does not yet exist.
    tempFile.source().buffered().use { input ->
        target.sink(append = false).use { output ->
            input.transferTo(output)
        }
    }

    return target.path
}
