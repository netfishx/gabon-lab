package lab.gabon.model

/** Video lifecycle status — matches Go VideoStatus* constants. */
enum class VideoStatus(val value: Short) {
    FAILED(0),
    PENDING_ENCODE(1),
    ENCODING(2),
    PENDING_REVIEW(3),
    APPROVED(4),
    REJECTED(5);

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Short): VideoStatus =
            map[value] ?: throw IllegalArgumentException("Unknown VideoStatus: $value")
    }
}

/** Play record type. */
enum class PlayType(val value: Short) {
    CLICK(1),
    VALID_PLAY(2);

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Short): PlayType =
            map[value] ?: throw IllegalArgumentException("Unknown PlayType: $value")
    }
}

/** Admin account status. */
enum class AdminStatus(val value: Short) {
    DISABLED(0),
    ACTIVE(1);

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Short): AdminStatus =
            map[value] ?: throw IllegalArgumentException("Unknown AdminStatus: $value")
    }
}

/** Admin role. */
enum class AdminRole(val value: Short) {
    SUPERADMIN(1),
    ADMIN(2);

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Short): AdminRole =
            map[value] ?: throw IllegalArgumentException("Unknown AdminRole: $value")
    }
}

/** Task type (period granularity). */
enum class TaskType(val value: Short) {
    DAILY(1),
    WEEKLY(2),
    MONTHLY(3);

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Short): TaskType =
            map[value] ?: throw IllegalArgumentException("Unknown TaskType: $value")
    }
}

/** Task progress status. */
enum class TaskStatus(val value: Short) {
    IN_PROGRESS(1),
    COMPLETED(2),
    CLAIMED(3),
    EXPIRED(4);

    companion object {
        private val map = entries.associateBy { it.value }
        fun fromValue(value: Short): TaskStatus =
            map[value] ?: throw IllegalArgumentException("Unknown TaskStatus: $value")
    }
}

// ── S3 Bucket Constants ─────────────────────────────────────
const val BUCKET_VIDEOS = "gabon-videos"
const val BUCKET_AVATARS = "gabon-avatars"
