package io.github.stevekk11.dtos

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object AbsenceHoursSerializer : KSerializer<AbsenceHours> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AbsenceHours") {
        element("from", JsonElement.serializer().descriptor)
        element("to", JsonElement.serializer().descriptor, isOptional = true)
    }

    override fun serialize(encoder: Encoder, value: AbsenceHours) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonObject = buildJsonObject {
            value.from?.let { put("from", JsonPrimitive(it)) }
            value.to?.let { put("to", JsonPrimitive(it)) }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): AbsenceHours {
        val jsonDecoder = decoder as JsonDecoder
        val jsonElement = jsonDecoder.decodeJsonElement()
        return when (jsonElement) {
            is JsonObject -> {
                val from = jsonElement["from"]?.jsonPrimitive?.intOrNull
                val to = jsonElement["to"]?.jsonPrimitive?.intOrNull
                AbsenceHours(from, to)
            }
            is JsonPrimitive -> {
                val from = jsonElement.intOrNull
                AbsenceHours(from, null)
            }
            else -> throw SerializationException("Unexpected JSON element for AbsenceHours: $jsonElement")
        }
    }
}