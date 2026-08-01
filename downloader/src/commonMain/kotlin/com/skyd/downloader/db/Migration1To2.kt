package com.skyd.downloader.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE Download ADD COLUMN outputUri TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE Download ADD COLUMN transcodeStatus TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE Download ADD COLUMN finalSize INTEGER NOT NULL DEFAULT 0")
    }
}
