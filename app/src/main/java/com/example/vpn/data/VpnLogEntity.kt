package com.example.vpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.vpn.model.VpnLogEntry

@Entity(tableName = "vpn_logs")
data class VpnLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
) {
    fun toDomain(): VpnLogEntry {
        val lvl = try {
            VpnLogEntry.LogLevel.valueOf(level)
        } catch (_: Exception) {
            VpnLogEntry.LogLevel.INFO
        }
        return VpnLogEntry(
            id = id,
            timestamp = timestamp,
            level = lvl,
            tag = tag,
            message = message
        )
    }

    companion object {
        fun fromDomain(entry: VpnLogEntry): VpnLogEntity {
            return VpnLogEntity(
                id = entry.id,
                timestamp = entry.timestamp,
                level = entry.level.name,
                tag = entry.tag,
                message = entry.message
            )
        }
    }
}
