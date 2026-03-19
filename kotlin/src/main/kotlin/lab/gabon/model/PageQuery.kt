package lab.gabon.model

import kotlinx.serialization.Serializable

/** Shared pagination query parameters. */
@Serializable
data class PageQuery(
    val page: Int = 1,
    val pageSize: Int = 10,
)
