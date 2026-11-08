# Changelog

## 0.2.0 (8 November 2026)

- `CmsWebhook.verify(secret, timestamp, signature, body)` checks a webhook message on a JVM server, such as Ktor or Spring Boot: the HMAC-SHA256 signature in `x-orbit-signature`, compared in constant time, and the `x-orbit-timestamp` within five minutes, the same rules as the JavaScript and PHP SDKs. `CmsWebhook.parse(…)` also answers the message's `event` and `data`, or `null` when it is not genuine. Both are static for Java callers and take the body as a `String` or a `ByteArray`.
- When the organisation's CMS Orbit trial or plan has ended, the delivery API and GraphQL answer 402. Every call then throws `CmsSubscriptionInactiveException` (status `402`, code `subscription_inactive`) straight away, without retrying, whatever code the API sends. The version is now `0.2.0`.
- `Images.srcSet` offers 320, 480, 640, 768, 960, 1280, 1600 and 1920 pixels wide instead of 640, 960, 1280 and 1920, the widths CMS Orbit makes, so phones and small cards load a photo close to the size they show it at.
- Pages carry `id`, the entry's id, and `insights`, a `CmsInsightsSite` when the space is connected to Insights Orbit. Both are optional and come last in `CmsPage`, so answers from an older CMS Orbit still decode and code that builds pages still compiles.
- `CmsInsights.tags(page)` writes the Insights Orbit script with the site key and `<meta name="insights-content">` with the entry's id, for server-rendered pages. With `waitForConsent`, the script waits for `insights.consent()` before counting. Without a site key it writes nothing.
