package com.skyd.podaura.model.repository.download

import io.github.vinceglb.filekit.PlatformFile

/**
 * Writes [tempFile] into the Storage Access Framework (SAF) directory
 * `{rootTreeUri}/{showName}/{fileName}`.
 *
 * On Android, [rootTreeUri] is a SAF tree `content://` URI obtained from a directory picker.
 * The [showName] subdirectory is created if it is absent. If a file named [fileName] already
 * exists in that directory, the suffix `" (2)"`, `" (3)"`, ... is inserted before the
 * extension (e.g. `episode.mp3` -> `episode (2).mp3` -> `episode (3).mp3`) until a free name
 * is found. A file with no extension gets the suffix appended to the whole name.
 *
 * The bytes of [tempFile] are streamed to the resolved SAF target; the temp file itself is
 * neither moved nor deleted. Returns the final target's URI/path string (a `content://`
 * document URI on Android).
 *
 * On non-Android targets this throws [UnsupportedOperationException].
 */
expect fun writeTranscodedToSaf(
    tempFile: PlatformFile,
    rootTreeUri: String,
    showName: String,
    fileName: String,
): String
