package lab.gabon.plugin

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Marks a field to preserve its @SerialName as-is, bypassing the global
 * SnakeCase naming strategy. Use together with @SerialName("camelCase").
 *
 * Required because kotlinx-serialization 1.10+ applies the naming strategy
 * to all serial names, including those set via @SerialName.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
annotation class JsonPreserve
