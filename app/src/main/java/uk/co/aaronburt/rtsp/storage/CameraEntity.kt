package uk.co.aaronburt.rtsp.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "mainUrl") val mainUrlCipher: String,
    @ColumnInfo(name = "subUrl") val subUrlCipher: String?,
    val username: String,
    val passwordCipher: String?,
    val transport: String,
    val latencyMs: Int,
    val autoReconnect: Boolean,
    val startMuted: Boolean,
    val favourite: Boolean,
    val createdAt: Long,
)

internal fun mainUrlContext(cameraId: String): String = "$cameraId/main"

internal fun subUrlContext(cameraId: String): String = "$cameraId/sub"
