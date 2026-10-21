package com.bifrostorbit.cms

import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Reads published content, and drafts for previews, from the CMS Orbit delivery API.
 *
 * ```
 * val cms = CmsOrbitClient("cms_pk_...") { defaultRegion = "ireland" }
 * val result = cms.resolve("/services/consultations")
 * ```
 *
 * Every call is a suspend function. Java callers use [async], which answers with `CompletableFuture`s.
 */
public class CmsOrbitClient private constructor(builder: Builder) {
    /** The delivery API address, such as `https://api.bifrostorbit.com/cms/api/cdn/v1`. */
    public val api: String

    /** The CMS address without the API path, used for GraphQL and preview tokens. */
    public val origin: String

    public val defaultRegion: String? = builder.defaultRegion
    public val defaultLocale: String? = builder.defaultLocale

    private val apiKey: String
    private val previewKey: String? = builder.previewKey
    private val timeoutMillis: Long = builder.timeoutMillis
    private val retries: Int = maxOf(0, builder.retries)
    private val retryDelays: List<Long> = builder.retryDelays
    private val http: OkHttpClient

    /** The same calls for Java, answering with `CompletableFuture`s. */
    public val async: CmsOrbitAsyncClient by lazy { CmsOrbitAsyncClient(this) }

    init {
        val baseUrl = builder.baseUrl.trim()
        val key = builder.apiKey.trim()
        require(baseUrl.isNotEmpty()) { "A baseUrl is required" }
        require(key.isNotEmpty()) { "An apiKey is required" }
        // [EXPLAIN-D249]
        if (builder.isAndroid && listOf(key, builder.previewKey).any { it?.startsWith(SECRET_KEY_PREFIX) == true }) {
            throw IllegalArgumentException("A secret CMS key must never be used in an Android app. Use a public cms_pk_ key here.")
        }
        api = apiBase(baseUrl)
        origin = api.replace(Regex("/api/cdn/v\\d+$"), "")
        apiKey = key
        http = (builder.okHttpClient ?: sharedClient).newBuilder()
            .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Where the client reads from when no [Builder.baseUrl] is given. */
    public enum class Environment(public val baseUrl: String) {
        PRODUCTION("https://api.bifrostorbit.com/cms"),
        STAGING("https://api-stage.bifrostorbit.com/cms"),
    }

    /** Settings for a [CmsOrbitClient]. Set properties in Kotlin, or chain the methods of the same name in Java. */
    public class Builder(public var apiKey: String) {
        /** The API address. Defaults to production, `https://api.bifrostorbit.com/cms`. */
        public var baseUrl: String = Environment.PRODUCTION.baseUrl

        /** A secret key (`cms_sk_`) for drafts. Server only: refused in Android apps. */
        public var previewKey: String? = null

        /** Used when a call names no region. */
        public var defaultRegion: String? = null

        /** Used when a call names no language. Without it, each region answers in its own default language. */
        public var defaultLocale: String? = null

        /** Per attempt, in milliseconds. 5 seconds by default. */
        public var timeoutMillis: Long = 5_000

        /** Extra attempts after a network error or a server error. 2 by default. */
        public var retries: Int = 2

        /** Your own OkHttp client, to share its connection pool, add interceptors or set a proxy. */
        public var okHttpClient: OkHttpClient? = null

        internal var retryDelays: List<Long> = listOf(250, 750, 2_000)
        internal var isAndroid: Boolean = runningOnAndroid()

        public fun baseUrl(baseUrl: String): Builder = apply { this.baseUrl = baseUrl }
        public fun environment(environment: Environment): Builder = apply { this.baseUrl = environment.baseUrl }
        public fun apiKey(apiKey: String): Builder = apply { this.apiKey = apiKey }
        public fun previewKey(previewKey: String?): Builder = apply { this.previewKey = previewKey }
        public fun defaultRegion(region: String?): Builder = apply { this.defaultRegion = region }
        public fun defaultLocale(locale: String?): Builder = apply { this.defaultLocale = locale }
        public fun timeoutMillis(millis: Long): Builder = apply { this.timeoutMillis = millis }
        public fun timeout(timeout: Duration): Builder = apply { this.timeoutMillis = timeout.inWholeMilliseconds }
        public fun retries(retries: Int): Builder = apply { this.retries = retries }
        public fun okHttpClient(client: OkHttpClient?): Builder = apply { this.okHttpClient = client }

        public fun build(): CmsOrbitClient = CmsOrbitClient(this)
    }

    public companion object {
        public const val PUBLIC_KEY_PREFIX: String = "cms_pk_"
        public const val SECRET_KEY_PREFIX: String = "cms_sk_"

        private val sharedClient: OkHttpClient by lazy { OkHttpClient() }
        private val jsonType = "application/json".toMediaType()

        @JvmStatic
        public fun builder(apiKey: String): Builder = Builder(apiKey)

        public operator fun invoke(apiKey: String, configure: Builder.() -> Unit = {}): CmsOrbitClient =
            Builder(apiKey).apply(configure).build()

        internal fun apiBase(baseUrl: String): String {
            val trimmed = baseUrl.trimEnd('/')
            return when {
                Regex("/api/cdn/v\\d+$").containsMatchIn(trimmed) -> trimmed
                trimmed.endsWith("/api/cdn") -> "$trimmed/v1"
                else -> "$trimmed/api/cdn/v1"
            }
        }

        private fun runningOnAndroid(): Boolean =
            try {
                Class.forName("android.os.Build")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
    }

    /** One page by its address. Throws [CmsNotFoundException] when nothing is published there. */
    @JvmSynthetic
    public suspend fun getPage(path: String, options: RequestOptions = RequestOptions.DEFAULT): CmsPage {
        val page = request(
            "/content",
            listOf("url" to path) + scope(options) + listOf("strict" to options.strict, include(options.includeReferences)),
            options.preview,
            CmsPage.serializer(),
        )
        return withMeta(page, options)
    }

    /** Like [getPage], but answers `null` when nothing is published at the address. */
    @JvmSynthetic
    public suspend fun findPage(path: String, options: RequestOptions = RequestOptions.DEFAULT): CmsPage? =
        orNull { getPage(path, options) }

    /**
     * Says what an address is: a page, a redirect, gone or not found. Call it before rendering a page.
     *
     * @param referrer The address of the page that linked here, recorded with addresses that are not found.
     */
    @JvmSynthetic
    public suspend fun resolve(
        path: String,
        options: RequestOptions = RequestOptions.DEFAULT,
        referrer: String? = null,
    ): CmsResolution {
        val data = requestData(
            endpoint("/resolve", listOf("url" to path) + scope(options) + listOf("strict" to options.strict, include(options.includeReferences)), options.preview),
            key(options.preview),
            headers = if (referrer.isNullOrEmpty()) emptyMap() else mapOf("x-cms-referrer" to referrer),
            answers = setOf(404, 410),
        )
        val result = data as? JsonObject ?: throw CmsDecodingException("The CMS answered with something other than a resolution")
        return when (val kind = (result["kind"] as? JsonPrimitive)?.contentOrNull) {
            "page" -> CmsResolution.Page(
                withMeta(CmsJson.decode(CmsPage.serializer(), result["page"] ?: throw CmsDecodingException("A page answer had no page")), options),
            )
            "redirect" -> {
                val status = (result["statusCode"] as? JsonPrimitive)?.intOrNull ?: 307
                CmsResolution.Redirect(
                    to = (result["destination"] as? JsonPrimitive)?.contentOrNull ?: throw CmsDecodingException("A redirect had no destination"),
                    status = status,
                    permanent = (result["permanent"] as? JsonPrimitive)?.booleanOrNull ?: (status == 301 || status == 308),
                    hops = (result["hops"] as? JsonPrimitive)?.intOrNull ?: 1,
                )
            }
            "gone" -> CmsResolution.Gone
            "notFound" -> CmsResolution.NotFound
            else -> throw CmsDecodingException("Unknown resolution kind: $kind")
        }
    }

    /** A menu by its key, such as `main`. */
    @JvmSynthetic
    public suspend fun getNavigation(key: String, options: RequestOptions = RequestOptions.DEFAULT): CmsNavigation =
        request("/navigation", listOf("key" to key) + scope(options), options.preview, CmsNavigation.serializer())

    @JvmSynthetic
    public suspend fun findNavigation(key: String, options: RequestOptions = RequestOptions.DEFAULT): CmsNavigation? =
        orNull { getNavigation(key, options) }

    /** A global entry, such as the footer, by its key. */
    @JvmSynthetic
    public suspend fun getGlobal(key: String, options: RequestOptions = RequestOptions.DEFAULT): CmsGlobal =
        request(
            "/global",
            listOf("key" to key) + scope(options) + listOf("strict" to options.strict, include(options.includeReferences)),
            options.preview,
            CmsGlobal.serializer(),
        )

    @JvmSynthetic
    public suspend fun findGlobal(key: String, options: RequestOptions = RequestOptions.DEFAULT): CmsGlobal? =
        orNull { getGlobal(key, options) }

    /** Entries of one content type, filtered and paged, with their fields when asked. */
    @JvmSynthetic
    public suspend fun list(options: ListOptions): CmsContentList {
        val include = listOfNotNull("fields".takeIf { options.includeFields }, "references".takeIf { options.includeReferences })
            .sorted().joinToString(",")
        val query = listOf(
            "type" to options.type,
            "region" to (options.region ?: defaultRegion),
            "locale" to (options.locale ?: defaultLocale),
            "page" to options.page,
            "perPage" to options.perPage,
            "tag" to options.tag,
            "category" to options.category,
            "sort" to options.sort?.value,
            "q" to options.q,
            "include" to include,
        ) + options.filter.map { (field, value) -> "filter[$field]" to value }
        return request("/list", query, options.preview, CmsContentList.serializer())
    }

    /** The regions the key may read, with their languages. */
    @JvmSynthetic
    public suspend fun getRegions(): List<CmsRegion> =
        request("/regions", emptyList(), false, ListSerializer(CmsRegion.serializer()))

    /** The languages of [region], or of the default region. With neither, every language the key may read. */
    @JvmSynthetic
    public suspend fun getLocales(region: String? = null): List<CmsLocale> {
        val regions = getRegions()
        val wanted = region ?: defaultRegion ?: return regions.flatMap { it.locales }
        return regions.find { it.key == wanted }?.locales ?: emptyList()
    }

    /** Every region's and language's sitemap. */
    @JvmSynthetic
    public suspend fun getSitemapIndex(): CmsSitemapIndex =
        request("/sitemap", emptyList(), false, CmsSitemapIndex.serializer())

    /** One sitemap file of a region, in its default language or [locale]. */
    @JvmSynthetic
    public suspend fun getSitemap(region: String? = null, locale: String? = null, page: Int? = null): CmsSitemap =
        request(
            "/sitemap",
            listOf("region" to (region ?: defaultRegion), "locale" to (locale ?: defaultLocale), "page" to page),
            false,
            CmsSitemap.serializer(),
        )

    /** The robots.txt for the environment. */
    @JvmSynthetic
    public suspend fun getRobots(): CmsRobots = request("/robots", emptyList(), false, CmsRobots.serializer())

    /** Popups live now for a page and the visitor's sign-in state, highest priority first. */
    @JvmSynthetic
    public suspend fun getPopups(
        path: String,
        loggedIn: Boolean = false,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CmsTimed<CmsPopup> =
        request(
            "/popups",
            listOf("path" to path, "loggedIn" to loggedIn) + scope(options),
            options.preview,
            CmsTimed.serializer(CmsPopup.serializer()),
        )

    /** Banners live now for a page, pinned first and then by priority. */
    @JvmSynthetic
    public suspend fun getBanners(
        path: String,
        loggedIn: Boolean = false,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CmsTimed<CmsBanner> =
        request(
            "/banners",
            listOf("path" to path, "loggedIn" to loggedIn) + scope(options),
            options.preview,
            CmsTimed.serializer(CmsBanner.serializer()),
        )

    /** Banners live now on any page, each with the [CmsBanner.paths] it targets, to read once and pick per page. */
    @JvmSynthetic
    public suspend fun getBannersForEveryPage(
        loggedIn: Boolean = false,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CmsTimed<CmsBanner> =
        request(
            "/banners",
            listOf("everyPage" to true, "loggedIn" to loggedIn) + scope(options),
            options.preview,
            CmsTimed.serializer(CmsBanner.serializer()),
        )

    /**
     * Shop products with live prices and stock.
     *
     * @param filter A taxonomy term value. Products tagged with it in any taxonomy field match.
     */
    @JvmSynthetic
    public suspend fun getProducts(
        page: Int? = null,
        perPage: Int? = null,
        filter: String? = null,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CmsProductPage =
        request(
            "/products",
            scope(options) + listOf("page" to page, "perPage" to perPage, "filter" to filter),
            options.preview,
            CmsProductPage.serializer(),
        )

    /** A taxonomy and its terms, labelled in [locale]. */
    @JvmSynthetic
    public suspend fun getTaxonomy(key: String, locale: String? = null): CmsTaxonomy {
        val url = api.toHttpUrl().newBuilder().addPathSegment("taxonomies").addPathSegment(key)
        (locale ?: defaultLocale)?.let { url.addQueryParameter("locale", it) }
        return CmsJson.decode(CmsTaxonomy.serializer(), requestData(url.build(), apiKey))
    }

    /** The space's content types and components, with their fields. */
    @JvmSynthetic
    public suspend fun getSchema(): CmsSchema = request("/schema", emptyList(), false, CmsSchema.serializer())

    /**
     * Runs a GraphQL query and answers with its `data`. Errors in the answer are thrown, not returned beside partial data.
     * [preview] sends the preview key; ask for drafts in the query itself.
     */
    @JvmSynthetic
    public suspend fun graphql(
        query: String,
        variables: JsonObject? = null,
        operationName: String? = null,
        preview: Boolean = false,
    ): JsonElement? {
        val payload = buildJsonObject {
            put("query", query)
            if (variables != null) put("variables", variables)
            if (operationName != null) put("operationName", operationName)
        }
        val request = Request.Builder()
            .url("$origin/api/cdn/graphql")
            .header("Authorization", "Bearer ${key(preview)}")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        val response = try {
            http.newCall(request).awaitRaw()
        } catch (error: IOException) {
            throw CmsServerException("The CMS could not be reached", cause = error)
        }
        val body = parse(response.body) as? JsonObject
        val first = (body?.get("errors") as? JsonArray)?.firstOrNull() as? JsonObject
        val message = (first?.get("message") as? JsonPrimitive)?.contentOrNull
        when {
            response.code == 401 -> throw CmsUnauthorizedException(message ?: "The API key was refused")
            response.code == 429 -> throw CmsRateLimitedException(message ?: "Too many requests", retryAfter(response.retryAfter))
            response.code >= 500 -> throw CmsServerException(message ?: "The CMS returned ${response.code}", response.code)
            first != null -> {
                val code = ((first["extensions"] as? JsonObject)?.get("code") as? JsonPrimitive)?.contentOrNull ?: "graphql_error"
                throw CmsOrbitException(response.code, code, message ?: "The query failed")
            }
        }
        return body?.get("data")?.takeUnless { it is JsonNull }
    }

    /** Runs a GraphQL query and decodes its `data` into [T]. */
    @JvmSynthetic
    public suspend inline fun <reified T> graphqlAs(
        query: String,
        variables: JsonObject? = null,
        operationName: String? = null,
        preview: Boolean = false,
    ): T = CmsJson.decode(serializer<T>(), graphql(query, variables, operationName, preview) ?: JsonNull)

    /**
     * Checks a preview token, the `preview` parameter the CMS editor adds to your website's address, and answers with
     * the entry it is for. Throws [CmsUnauthorizedException] when the token is invalid or has expired. Read the draft
     * with `preview = true` once the token checks out.
     */
    @JvmSynthetic
    public suspend fun verifyPreviewToken(token: String): CmsPreviewInfo {
        val url = "$origin/preview".toHttpUrl().newBuilder().addQueryParameter("token", token).build()
        return CmsJson.decode(CmsPreviewInfo.serializer(), requestData(url, auth = null, retry = false))
    }

    private fun scope(options: RequestOptions): List<Pair<String, Any?>> =
        listOf("region" to (options.region ?: defaultRegion), "locale" to (options.locale ?: defaultLocale))

    private fun include(references: Boolean): Pair<String, Any?> = "include" to if (references) "references" else null

    private fun withMeta(page: CmsPage, options: RequestOptions): CmsPage =
        page.copy(meta = page.meta.copy(references = options.includeReferences))

    private suspend fun <T> orNull(call: suspend () -> T): T? =
        try {
            call()
        } catch (_: CmsNotFoundException) {
            null
        }

    private fun key(preview: Boolean): String {
        if (!preview) return apiKey
        return checkNotNull(previewKey) { "Previewing needs a previewKey on the client" }
    }

    private fun endpoint(path: String, query: List<Pair<String, Any?>>, preview: Boolean): HttpUrl {
        val builder = "$api$path".toHttpUrl().newBuilder()
        for ((name, value) in query + ("draft" to preview)) {
            if (value == null || value == false || value == "") continue
            builder.addQueryParameter(name, if (value == true) "true" else value.toString())
        }
        return builder.build()
    }

    private suspend fun <T> request(path: String, query: List<Pair<String, Any?>>, preview: Boolean, serializer: KSerializer<T>): T {
        val auth = key(preview)
        return CmsJson.decode(serializer, requestData(endpoint(path, query, preview), auth))
    }

    private suspend fun requestData(
        url: HttpUrl,
        auth: String?,
        headers: Map<String, String> = emptyMap(),
        answers: Set<Int> = emptySet(),
        retry: Boolean = true,
    ): JsonElement {
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (auth != null) header("Authorization", "Bearer $auth")
            headers.forEach { (name, value) -> header(name, value) }
        }.build()

        var lastError: CmsOrbitException? = null
        val attempts = if (retry) retries else 0
        for (attempt in 0..attempts) {
            if (attempt > 0) delay(retryDelays.getOrElse(attempt - 1) { 2_000 })
            val response = try {
                http.newCall(request).awaitRaw()
            } catch (error: IOException) {
                lastError = CmsServerException(
                    if (error is InterruptedIOException) "The CMS did not answer within ${timeoutMillis}ms" else "The CMS could not be reached",
                    cause = error,
                )
                continue
            }

            if (response.isSuccessful || response.code in answers) {
                val body = parse(response.body) as? JsonObject
                return body?.get("data") ?: throw CmsDecodingException("The CMS answered ${response.code} without data")
            }

            val error = (parse(response.body) as? JsonObject)?.let(::errorOf)
            val message = error?.second ?: "The CMS returned ${response.code}"
            when {
                response.code >= 500 -> {
                    lastError = CmsServerException(message, response.code)
                    continue
                }
                response.code == 401 -> throw CmsUnauthorizedException(message)
                response.code == 404 -> throw CmsNotFoundException(message)
                response.code == 429 -> throw CmsRateLimitedException(message, retryAfter(response.retryAfter))
                else -> throw CmsOrbitException(response.code, error?.first ?: "error", message)
            }
        }
        throw lastError ?: CmsServerException("The CMS could not be reached")
    }

    private fun errorOf(body: JsonObject): Pair<String?, String?> =
        when (val error = body["error"]) {
            is JsonObject -> (error["code"] as? JsonPrimitive)?.contentOrNull to (error["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> error.contentOrNull to (body["message"] as? JsonPrimitive)?.contentOrNull
            else -> null to null
        }

    private fun retryAfter(header: String?): Int = header?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: 60

    private fun parse(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            null
        }
}
