package com.example.vpn.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnServerDao {
    @Query("SELECT * FROM vpn_servers ORDER BY isFavorite DESC, pingMs ASC, name ASC")
    fun getAllServers(): Flow<List<VpnServerEntity>>

    @Query("SELECT * FROM vpn_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): VpnServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: VpnServerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<VpnServerEntity>)

    @Update
    suspend fun updateServer(server: VpnServerEntity)

    @Delete
    suspend fun deleteServer(server: VpnServerEntity)

    @Query("DELETE FROM vpn_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)

    @Query("UPDATE vpn_servers SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE vpn_servers SET pingMs = :pingMs, loadPercent = :loadPercent WHERE id = :id")
    suspend fun updateTelemetry(id: String, pingMs: Int, loadPercent: Int)

    @Query("SELECT COUNT(*) FROM vpn_servers")
    suspend fun getServerCount(): Int
}

@Dao
interface VpnLogDao {
    @Query("SELECT * FROM vpn_logs ORDER BY timestamp DESC LIMIT 500")
    fun getRecentLogs(): Flow<List<VpnLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VpnLogEntity)

    @Query("DELETE FROM vpn_logs")
    suspend fun clearLogs()
}
