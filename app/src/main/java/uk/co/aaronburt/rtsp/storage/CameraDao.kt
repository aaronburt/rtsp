package uk.co.aaronburt.rtsp.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CameraDao {
    @Query("SELECT * FROM cameras")
    suspend fun all(): List<CameraEntity>

    @Upsert
    suspend fun upsert(entity: CameraEntity)

    @Query("DELETE FROM cameras WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE cameras SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: String, favourite: Boolean)
}
