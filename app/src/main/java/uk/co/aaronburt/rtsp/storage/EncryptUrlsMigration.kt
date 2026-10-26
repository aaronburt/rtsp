package uk.co.aaronburt.rtsp.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal class EncryptUrlsMigration(
    private val cipher: CredentialCipher,
) : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
        val rows = db.query("SELECT id, mainUrl, subUrl FROM cameras").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacyUrls(
                            id = cursor.getString(0),
                            main = cursor.getString(1),
                            sub = if (cursor.isNull(2)) null else cursor.getString(2),
                        ),
                    )
                }
            }
        }
        rows.forEach { row ->
            db.execSQL(
                "UPDATE cameras SET mainUrl = ?, subUrl = ? WHERE id = ?",
                arrayOf(
                    cipher.encrypt(row.main, mainUrlContext(row.id)),
                    row.sub?.let { cipher.encrypt(it, subUrlContext(row.id)) },
                    row.id,
                ),
            )
        }
    }

    private data class LegacyUrls(
        val id: String,
        val main: String,
        val sub: String?,
    )
}
