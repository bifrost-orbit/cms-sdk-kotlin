package com.bifrostorbit.cms

/** A failed call. [status] is the HTTP status and [code] the API's error code, such as `bad_request`. */
public open class CmsOrbitException(
    public val status: Int,
    public val code: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A missing, unknown, expired or refused key, or a region or language the key may not read. */
public class CmsUnauthorizedException(message: String) : CmsOrbitException(401, "unauthorized", message)

/** Nothing published at the address, or no menu, global entry, taxonomy, region or language with that key. */
public class CmsNotFoundException(message: String) : CmsOrbitException(404, "not_found", message)

/** The organisation's CMS Orbit trial or plan has ended, so delivery is paused. Never retried. */
public class CmsSubscriptionInactiveException(
    message: String = "Delivery is paused because the organisation's CMS Orbit trial or plan has ended. Nothing is deleted, and it comes back as soon as an owner chooses a plan.",
) : CmsOrbitException(402, "subscription_inactive", message)

/** Too many requests. [retryAfter] is the number of seconds to wait. */
public class CmsRateLimitedException(message: String, public val retryAfter: Int) :
    CmsOrbitException(429, "rate_limited", message)

/** The CMS failed, could not be reached or did not answer in time, after every retry. */
public class CmsServerException(message: String, status: Int = 503, cause: Throwable? = null) :
    CmsOrbitException(status, "unavailable", message, cause)

/** The answer could not be read into the expected type. */
public class CmsDecodingException(message: String, cause: Throwable? = null) :
    CmsOrbitException(0, "decoding_error", message, cause)
