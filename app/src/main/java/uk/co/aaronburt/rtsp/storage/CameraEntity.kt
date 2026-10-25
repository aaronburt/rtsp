package uk.co.aaronburt.rtsp.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mainUrl: String,
    val subUrl: String?,
    val username: String,
    val passwordCipher: String?,
    val transport: String,
    val latencyMs: Int,
    val autoReconnect: Boolean,
    val startMuted: Boolean,
    val favourite: Boolean,
    val createdAt: Long,
)
