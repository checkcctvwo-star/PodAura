package com.skyd.podaura.model.repository.download

actual fun persistSafPermission(uri: String) {
    // SAF is Android-only; no-op on Apple platforms.
}
