package lab.gabon.repository

import lab.gabon.config.dbQuery
import org.jetbrains.exposed.v1.jdbc.insert

class PlayRecordRepo {
    suspend fun create(
        videoId: Long,
        customerId: Long?,
        playType: Short,
        ipAddress: String? = null,
    ): Unit =
        dbQuery {
            VideoPlayRecords.insert {
                it[VideoPlayRecords.videoId] = videoId
                it[VideoPlayRecords.customerId] = customerId
                it[VideoPlayRecords.playType] = playType
                it[VideoPlayRecords.ipAddress] = ipAddress
            }
        }
}
