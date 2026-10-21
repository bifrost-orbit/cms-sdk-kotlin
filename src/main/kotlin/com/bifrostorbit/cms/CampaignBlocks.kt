package com.bifrostorbit.cms

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One block of a banner or popup. Types this version does not know arrive as [Unknown]. */
@Serializable(with = CmsCampaignBlockSerializer::class)
public sealed interface CmsCampaignBlock {
    public val id: String
    public val type: String

    @Serializable
    public data class Image(override val id: String, val image: CmsImage? = null) : CmsCampaignBlock {
        override val type: String get() = "image"
    }

    /** [size] is `large`, `medium` or `eyebrow`. */
    @Serializable
    public data class Heading(override val id: String, val text: String, val size: String = "medium") : CmsCampaignBlock {
        override val type: String get() = "heading"
    }

    /** [body] is rich text as HTML. */
    @Serializable
    public data class Text(override val id: String, val body: String) : CmsCampaignBlock {
        override val type: String get() = "text"
    }

    @Serializable
    public data class Message(
        override val id: String,
        val text: String,
        val segments: List<CmsTextSegment> = emptyList(),
    ) : CmsCampaignBlock {
        override val type: String get() = "message"
    }

    /** [style] is `primary`, `secondary` or `link`. */
    @Serializable
    public data class Button(
        override val id: String,
        val text: String,
        val link: CmsLink? = null,
        val style: String = "primary",
    ) : CmsCampaignBlock {
        override val type: String get() = "button"
    }

    @Serializable
    public data class PromoCode(override val id: String, val code: String, val label: String? = null) : CmsCampaignBlock {
        override val type: String get() = "promoCode"
    }

    @Serializable
    public data class Terms(override val id: String, val text: String) : CmsCampaignBlock {
        override val type: String get() = "terms"
    }

    @Serializable
    public data class Form(
        override val id: String,
        val form: String,
        val submitText: String? = null,
    ) : CmsCampaignBlock {
        override val type: String get() = "form"
    }

    @Serializable
    public data class Countdown(override val id: String, val countdown: CmsCountdown) : CmsCampaignBlock {
        override val type: String get() = "countdown"
    }

    @Serializable(with = UnknownSerializer::class)
    public data class Unknown(override val id: String, override val type: String, val raw: JsonObject) : CmsCampaignBlock
}

internal object UnknownSerializer : KSerializer<CmsCampaignBlock.Unknown> {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor("CmsCampaignBlock.Unknown", JsonObject.serializer().descriptor)

    override fun deserialize(decoder: Decoder): CmsCampaignBlock.Unknown {
        val raw = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return CmsCampaignBlock.Unknown(
            id = (raw["id"] as? JsonPrimitive)?.contentOrNull ?: "",
            type = (raw["type"] as? JsonPrimitive)?.contentOrNull ?: "",
            raw = raw,
        )
    }

    override fun serialize(encoder: Encoder, value: CmsCampaignBlock.Unknown) {
        (encoder as JsonEncoder).encodeJsonElement(value.raw)
    }
}

internal object CmsCampaignBlockSerializer : JsonContentPolymorphicSerializer<CmsCampaignBlock>(CmsCampaignBlock::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<CmsCampaignBlock> =
        when ((element.jsonObject["type"] as? JsonPrimitive)?.contentOrNull) {
            "image" -> CmsCampaignBlock.Image.serializer()
            "heading" -> CmsCampaignBlock.Heading.serializer()
            "text" -> CmsCampaignBlock.Text.serializer()
            "message" -> CmsCampaignBlock.Message.serializer()
            "button" -> CmsCampaignBlock.Button.serializer()
            "promoCode" -> CmsCampaignBlock.PromoCode.serializer()
            "terms" -> CmsCampaignBlock.Terms.serializer()
            "form" -> CmsCampaignBlock.Form.serializer()
            "countdown" -> CmsCampaignBlock.Countdown.serializer()
            else -> UnknownSerializer
        }
}
