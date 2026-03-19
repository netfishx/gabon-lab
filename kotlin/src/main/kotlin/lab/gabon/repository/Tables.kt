package lab.gabon.repository

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

// --- admin_users ---
object AdminUsers : LongIdTable("admin_users") {
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val role = short("role").default(2)
    val fullName = varchar("full_name", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val status = short("status").default(1)
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

// --- customers ---
object Customers : LongIdTable("customers") {
    val username = varchar("username", 100)
    val passwordHash = varchar("password_hash", 255)
    val name = varchar("name", 255).nullable()
    val phone = varchar("phone", 50).nullable()
    val email = varchar("email", 255).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val signature = varchar("signature", 255).nullable()
    val isVip = bool("is_vip").default(false)
    val diamondBalance = long("diamond_balance").default(0)
    val withdrawalPasswordHash = varchar("withdrawal_password_hash", 255).nullable()
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

// --- videos ---
object Videos : LongIdTable("videos") {
    val customerId = reference("customer_id", Customers)
    val title = varchar("title", 500).nullable()
    val description = text("description").nullable()
    val fileName = varchar("file_name", 255)
    val fileSize = long("file_size")
    val fileUrl = varchar("file_url", 500)
    val thumbnailUrl = varchar("thumbnail_url", 500).nullable()
    val previewGifUrl = varchar("preview_gif_url", 500).nullable()
    val mimeType = varchar("mime_type", 100)
    val duration = integer("duration").nullable()
    val width = integer("width").nullable()
    val height = integer("height").nullable()
    val status = short("status").default(1)
    val reviewNotes = text("review_notes").nullable()
    val reviewedBy = optReference("reviewed_by", AdminUsers, onDelete = ReferenceOption.NO_ACTION)
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val totalClicks = long("total_clicks").default(0)
    val validClicks = long("valid_clicks").default(0)
    val likeCount = long("like_count").default(0)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
}

// --- video_play_records ---
object VideoPlayRecords : LongIdTable("video_play_records") {
    val videoId = reference("video_id", Videos)
    val customerId = optReference("customer_id", Customers)
    val playType = short("play_type")
    val ipAddress = varchar("ip_address", 45).nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}

// --- video_likes ---
object VideoLikes : LongIdTable("video_likes") {
    val videoId = reference("video_id", Videos)
    val customerId = reference("customer_id", Customers)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(videoId, customerId)
    }
}

// --- user_follows ---
object UserFollows : LongIdTable("user_follows") {
    val followerId = reference("follower_id", Customers)
    val followedId = reference("followed_id", Customers)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(followerId, followedId)
        check("chk_no_self_follow") { followerId neq followedId }
    }
}

// --- task_definitions ---
object TaskDefinitions : LongIdTable("task_definitions") {
    val taskCode = varchar("task_code", 100).uniqueIndex()
    val taskName = varchar("task_name", 255)
    val description = text("description").nullable()
    val taskType = short("task_type")
    val taskCategory = short("task_category")
    val targetCount = integer("target_count")
    val rewardDiamonds = integer("reward_diamonds")
    val iconUrl = varchar("icon_url", 500).nullable()
    val displayOrder = integer("display_order").default(0)
    val vipOnly = bool("vip_only").default(false)
    val status = short("status").default(1)
    val startTime = timestampWithTimeZone("start_time").nullable()
    val endTime = timestampWithTimeZone("end_time").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}

// --- task_progress ---
object TaskProgress : LongIdTable("task_progress") {
    val customerId = reference("customer_id", Customers)
    val taskId = reference("task_id", TaskDefinitions)
    val currentCount = integer("current_count").default(0)
    val targetCount = integer("target_count")
    val periodKey = varchar("period_key", 50)
    val taskStatus = short("task_status").default(1)
    val rewardDiamonds = integer("reward_diamonds")
    val completedAt = timestampWithTimeZone("completed_at").nullable()
    val claimedAt = timestampWithTimeZone("claimed_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(customerId, taskId, periodKey)
    }
}

// --- customer_sign_in_records ---
object CustomerSignInRecords : LongIdTable("customer_sign_in_records") {
    val customerId = reference("customer_id", Customers)
    val periodKey = varchar("period_key", 50)
    val rewardDiamonds = integer("reward_diamonds")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(customerId, periodKey)
    }
}
