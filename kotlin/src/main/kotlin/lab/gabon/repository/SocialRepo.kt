package lab.gabon.repository

import lab.gabon.config.dbQuery
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

data class FollowUserRow(
    val userId: Long,
    val username: String,
    val name: String?,
    val avatarUrl: String?,
    val followStatus: Int,
)

class SocialRepo {

    /**
     * INSERT ON CONFLICT DO NOTHING. Returns true if a row was actually inserted.
     */
    suspend fun follow(followerId: Long, followedId: Long): Boolean = dbQuery {
        val result = UserFollows.insertIgnore {
            it[UserFollows.followerId] = followerId
            it[UserFollows.followedId] = followedId
        }
        result.insertedCount > 0
    }

    /**
     * DELETE the follow relationship. Returns true if a row was deleted.
     */
    suspend fun unfollow(followerId: Long, followedId: Long): Boolean = dbQuery {
        val deleted = UserFollows.deleteWhere {
            (UserFollows.followerId eq followerId) and (UserFollows.followedId eq followedId)
        }
        deleted > 0
    }

    /**
     * Check if followerId follows followedId.
     */
    suspend fun isFollowing(followerId: Long, followedId: Long): Boolean = dbQuery {
        UserFollows
            .selectAll()
            .where {
                (UserFollows.followerId eq followerId) and (UserFollows.followedId eq followedId)
            }
            .count() > 0
    }

    /**
     * Compute follow status: 0=none, 1=one-way (viewer follows target), 2=mutual.
     * Returns 0 if viewerId is null or equals targetId.
     */
    suspend fun getFollowStatus(viewerId: Long?, targetId: Long): Int {
        if (viewerId == null || viewerId == targetId) return 0

        return dbQuery {
            val forward = UserFollows
                .selectAll()
                .where {
                    (UserFollows.followerId eq viewerId) and (UserFollows.followedId eq targetId)
                }
                .count() > 0

            if (!forward) return@dbQuery 0

            val reverse = UserFollows
                .selectAll()
                .where {
                    (UserFollows.followerId eq targetId) and (UserFollows.followedId eq viewerId)
                }
                .count() > 0

            if (reverse) 2 else 1
        }
    }

    /**
     * List users that [userId] is following, with follow_status relative to [viewerId].
     */
    suspend fun listFollowing(
        userId: Long,
        page: Int,
        pageSize: Int,
        viewerId: Long?,
    ): Pair<List<FollowUserRow>, Long> = dbQuery {
        val total = UserFollows
            .selectAll()
            .where { UserFollows.followerId eq userId }
            .count()

        val rows = UserFollows
            .join(Customers, JoinType.INNER, UserFollows.followedId, Customers.id)
            .selectAll()
            .where {
                (UserFollows.followerId eq userId) and Customers.deletedAt.isNull()
            }
            .orderBy(UserFollows.createdAt)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .map { row ->
                val targetId = row[UserFollows.followedId].value
                FollowUserRow(
                    userId = targetId,
                    username = row[Customers.username],
                    name = row[Customers.name],
                    avatarUrl = row[Customers.avatarUrl],
                    followStatus = 0, // placeholder, computed below
                )
            }

        val withStatus = computeFollowStatuses(rows, viewerId)
        withStatus to total
    }

    /**
     * List followers of [userId], with follow_status relative to [viewerId].
     */
    suspend fun listFollowers(
        userId: Long,
        page: Int,
        pageSize: Int,
        viewerId: Long?,
    ): Pair<List<FollowUserRow>, Long> = dbQuery {
        val total = UserFollows
            .selectAll()
            .where { UserFollows.followedId eq userId }
            .count()

        val rows = UserFollows
            .join(Customers, JoinType.INNER, UserFollows.followerId, Customers.id)
            .selectAll()
            .where {
                (UserFollows.followedId eq userId) and Customers.deletedAt.isNull()
            }
            .orderBy(UserFollows.createdAt)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .map { row ->
                val followerId = row[UserFollows.followerId].value
                FollowUserRow(
                    userId = followerId,
                    username = row[Customers.username],
                    name = row[Customers.name],
                    avatarUrl = row[Customers.avatarUrl],
                    followStatus = 0,
                )
            }

        val withStatus = computeFollowStatuses(rows, viewerId)
        withStatus to total
    }

    suspend fun getFollowingCount(userId: Long): Long = dbQuery {
        UserFollows
            .selectAll()
            .where { UserFollows.followerId eq userId }
            .count()
    }

    suspend fun getFollowerCount(userId: Long): Long = dbQuery {
        UserFollows
            .selectAll()
            .where { UserFollows.followedId eq userId }
            .count()
    }

    /**
     * Batch-compute follow statuses for a list of users relative to [viewerId].
     * Must be called inside a transaction (from dbQuery block).
     */
    private fun computeFollowStatuses(
        rows: List<FollowUserRow>,
        viewerId: Long?,
    ): List<FollowUserRow> {
        if (viewerId == null || rows.isEmpty()) return rows

        val targetIds = rows.map { it.userId }

        // viewer -> target (forward)
        val forwardSet = UserFollows
            .selectAll()
            .where {
                (UserFollows.followerId eq viewerId) and
                    (UserFollows.followedId inList targetIds)
            }
            .map { it[UserFollows.followedId].value }
            .toSet()

        // target -> viewer (reverse)
        val reverseSet = UserFollows
            .selectAll()
            .where {
                (UserFollows.followedId eq viewerId) and
                    (UserFollows.followerId inList targetIds)
            }
            .map { it[UserFollows.followerId].value }
            .toSet()

        return rows.map { row ->
            val forward = row.userId in forwardSet
            val reverse = row.userId in reverseSet
            val status = when {
                forward && reverse -> 2
                forward -> 1
                else -> 0
            }
            row.copy(followStatus = status)
        }
    }
}
