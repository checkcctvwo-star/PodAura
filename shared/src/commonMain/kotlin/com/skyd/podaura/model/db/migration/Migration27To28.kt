package com.skyd.podaura.model.db.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.skyd.podaura.model.bean.article.RSS_MEDIA_TABLE_NAME
import com.skyd.podaura.model.bean.article.RssMediaBean

class Migration27To28 : Migration(27, 28) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `$RSS_MEDIA_TABLE_NAME` " +
                    "ADD ${RssMediaBean.SEASON_COLUMN} TEXT"
        )
    }
}
