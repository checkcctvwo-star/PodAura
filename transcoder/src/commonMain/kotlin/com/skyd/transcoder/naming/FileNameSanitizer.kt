package com.skyd.transcoder.naming

/**
 * Sanitize a single path segment (file or folder name): replace illegal chars `\ / : * ? " < > |`
 * with `_`, collapse consecutive underscores, trim leading/trailing spaces/dots/underscores,
 * truncate to [maxLength].
 */
internal fun sanitizeFileNameSegment(input: String, maxLength: Int = 200): String {
    var s = input.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    s = s.replace(Regex("_+"), "_")
    s = s.trim().trim('.').trim('_').trim()
    if (s.length > maxLength) s = s.take(maxLength)
    return s
}

/**
 * Sanitize a file name with optional extension. Extension is preserved (not truncated).
 */
fun sanitizeFileName(name: String, extension: String? = null, maxLength: Int = 200): String {
    val base = sanitizeFileNameSegment(name, maxLength)
    val ext = extension?.trim()?.trimStart('.')
    return if (ext.isNullOrEmpty()) base else "$base.$ext"
}