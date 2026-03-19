package lab.gabon.plugin

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * SnakeCase naming strategy that respects @JsonPreserve: fields annotated
 * with @JsonPreserve keep their serial name unchanged.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val PreserveAwareSnakeCase =
    JsonNamingStrategy { descriptor: SerialDescriptor, elementIndex: Int, serialName: String ->
        val annotations = descriptor.getElementAnnotations(elementIndex)
        if (annotations.any { it is JsonPreserve }) {
            serialName
        } else {
            JsonNamingStrategy.SnakeCase.serialNameForJson(descriptor, elementIndex, serialName)
        }
    }

@OptIn(ExperimentalSerializationApi::class)
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                namingStrategy = PreserveAwareSnakeCase
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
}
