package com.skyd.podaura.model.repository.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.skyd.fundation.di.get

actual fun persistSafPermission(uri: String) {
    runCatching {
        val parsed = Uri.parse(uri)
        get<Context>().contentResolver.takePersistableUriPermission(
            parsed,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}
