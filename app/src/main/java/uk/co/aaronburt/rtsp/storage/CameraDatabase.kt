package uk.co.aaronburt.rtsp.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CameraEntity::class], version = 2, exportSchema = true)
abstract class CameraDatabase : RoomDatabase() {
    abstract fun cameraDao(): CameraDao

    companion object {
        const val FILE_NAME = "cameras.db"

        fun open(
            context: Context,
            name: String = FILE_NAME,
            cipher: CredentialCipher = KeystoreKey.cipher(),
        ): CameraDatabase =
            Room.databaseBuilder(context.applicationContext, CameraDatabase::class.java, name)
                .addMigrations(EncryptUrlsMigration(cipher))
                .build()
    }
}
