package app.rocat.core.common.util

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared JSON helpers mirroring mihon's configuration
 * (`ignoreUnknownKeys = true` keeps scripts resilient to schema drift).
 */
object JsonUtil {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

inline fun <reified T> String.parseJson(): T = JsonUtil.json.decodeFromString(this)

inline fun <reified T> T.toJsonString(): String = JsonUtil.json.encodeToString(this)
