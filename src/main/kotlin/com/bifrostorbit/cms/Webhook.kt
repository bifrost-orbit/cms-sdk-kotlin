package com.bifrostorbit.cms

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A webhook message from CMS Orbit that passed [CmsWebhook.parse]. */
public data class CmsWebhookMessage(
    /** Such as `content.published`, `navigation.changed` or `webhook.test`. */
    val event: String,
    val data: JsonObject = JsonObject(emptyMap()),
)

// [EXPLAIN-D525]
/** Checks webhook messages from CMS Orbit on a JVM server, with the same rules as the JavaScript SDK's webhook handler. */
public object CmsWebhook {
    /** Messages whose `x-orbit-timestamp` is further than this from the clock are refused. */
    public const val MAX_AGE_MS: Long = 5 * 60 * 1000

    public const val TIMESTAMP_HEADER: String = "x-orbit-timestamp"
    public const val SIGNATURE_HEADER: String = "x-orbit-signature"

    /**
     * True when [signature], the `x-orbit-signature` header, is the HMAC-SHA256 of `timestamp.body` keyed with the
     * webhook [secret], and [timestamp], the `x-orbit-timestamp` header in milliseconds, is within five minutes of [nowMs].
     * Pass the raw body, before any JSON parsing.
     *
     * @throws IllegalArgumentException when the secret is empty.
     */
    @JvmStatic
    @JvmOverloads
    public fun verify(
        secret: String,
        timestamp: String,
        signature: String,
        body: ByteArray,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        require(secret.isNotEmpty()) { "The webhook secret is required" }
        if (timestamp.isEmpty() || signature.isEmpty() || !timestamp.all { it in '0'..'9' }) return false
        val sent = timestamp.toLongOrNull() ?: return false
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(timestamp.toByteArray(Charsets.UTF_8))
        mac.update('.'.code.toByte())
        val expected = mac.doFinal(body).joinToString("") { "%02x".format(it) }
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))) return false
        return Math.abs(nowMs - sent) <= MAX_AGE_MS
    }

    @JvmStatic
    @JvmOverloads
    public fun verify(
        secret: String,
        timestamp: String,
        signature: String,
        body: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = verify(secret, timestamp, signature, body.toByteArray(Charsets.UTF_8), nowMs)

    /**
     * Verifies a message and answers its `event` and `data`, or `null` when it is not genuine, is too old or is not JSON.
     *
     * @throws IllegalArgumentException when the secret is empty.
     */
    @JvmStatic
    @JvmOverloads
    public fun parse(
        secret: String,
        timestamp: String,
        signature: String,
        body: ByteArray,
        nowMs: Long = System.currentTimeMillis(),
    ): CmsWebhookMessage? {
        if (!verify(secret, timestamp, signature, body, nowMs)) return null
        val text = body.toString(Charsets.UTF_8)
        val message = runCatching { CmsJson.json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val event = (message["event"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        return CmsWebhookMessage(event, message["data"] as? JsonObject ?: JsonObject(emptyMap()))
    }

    @JvmStatic
    @JvmOverloads
    public fun parse(
        secret: String,
        timestamp: String,
        signature: String,
        body: String,
        nowMs: Long = System.currentTimeMillis(),
    ): CmsWebhookMessage? = parse(secret, timestamp, signature, body.toByteArray(Charsets.UTF_8), nowMs)
}
