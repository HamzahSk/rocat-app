package app.rocat.scripting.api.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes a [List] of strings as a single comma separated string when stored on
 * disk, mirroring mihon's `StringListColumnAdapter` for its SQL database.
 */
object StringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScriptStringList", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeString(value.joinToString("\u001E"))
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val raw = decoder.decodeString()
        if (raw.isEmpty()) return emptyList()
        return raw.split("\u001E")
    }
}