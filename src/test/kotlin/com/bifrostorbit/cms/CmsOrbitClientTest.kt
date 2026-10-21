package com.bifrostorbit.cms

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit

class CmsOrbitClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun client(configure: CmsOrbitClient.Builder.() -> Unit = {}): CmsOrbitClient =
        CmsOrbitClient.Builder("cms_pk_test").apply {
            baseUrl = server.url("/cms").toString()
            retryDelays = listOf(1, 1, 1)
            isAndroid = false
            configure()
        }.build()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    private fun answer(body: String, code: Int = 200, vararg headers: Pair<String, String>) {
        server.enqueue(
            MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }.build(),
        )
    }

    private fun taken(): RecordedRequest = checkNotNull(server.takeRequest(1, TimeUnit.SECONDS))

    @Test
    fun `builds the API address like the JavaScript SDK`() {
        assertEquals("https://api.bifrostorbit.com/cms/api/cdn/v1", CmsOrbitClient.apiBase("https://api.bifrostorbit.com/cms/"))
        assertEquals("https://example.com/cms/api/cdn/v1", CmsOrbitClient.apiBase("https://example.com/cms/api/cdn"))
        assertEquals("https://example.com/cms/api/cdn/v2", CmsOrbitClient.apiBase("https://example.com/cms/api/cdn/v2"))
        val cms = CmsOrbitClient("cms_pk_test") { isAndroid = false }
        assertEquals("https://api.bifrostorbit.com/cms/api/cdn/v1", cms.api)
        assertEquals("https://api.bifrostorbit.com/cms", cms.origin)
        val staging = CmsOrbitClient.builder("cms_pk_test").environment(CmsOrbitClient.Environment.STAGING).build()
        assertEquals("https://api-stage.bifrostorbit.com/cms", staging.origin)
    }

    @Test
    fun `getPage sends the address, scope, key and include, and decodes the page`() = runBlocking {
        answer(fixture("content.json"))
        val cms = client { defaultRegion = "ireland" }

        val page = cms.getPage("/services/consultations", RequestOptions(locale = "ga-IE", strict = true, includeReferences = true))

        val request = taken()
        assertEquals("GET", request.method)
        assertEquals("/cms/api/cdn/v1/content", request.url.encodedPath)
        assertEquals("/services/consultations", request.url.queryParameter("url"))
        assertEquals("ireland", request.url.queryParameter("region"))
        assertEquals("ga-IE", request.url.queryParameter("locale"))
        assertEquals("true", request.url.queryParameter("strict"))
        assertEquals("references", request.url.queryParameter("include"))
        assertNull(request.url.queryParameter("draft"))
        assertEquals("Bearer cms_pk_test", request.headers["Authorization"])

        assertEquals("Consultations", page.name)
        assertEquals(7, page.version)
        assertEquals("Consultations | Acme Clinic", page.seo.title)
        assertEquals("en_IE", page.seo.openGraph.locale)
        assertEquals("WebPage", page.seo.jsonLd.single()["@type"]?.jsonPrimitive?.content)
        val block = page.components.single()
        assertEquals("card-grid", block.type)
        assertEquals("wide", block.variant)
        assertEquals("card", block.children.getValue("cards").single().type)
        assertEquals(CmsPageMeta("ireland", "en-IE", "ireland", "ga-IE", true, false, false, references = true), page.meta)
    }

    @Test
    fun `leaves out empty options`() = runBlocking {
        answer(fixture("content.json"))
        client().getPage("/")
        val url = taken().url
        assertEquals(listOf("url"), url.queryParameterNames.toList())
    }

    @Test
    fun `decodes fields into app-defined types`() = runBlocking {
        answer(fixture("content.json"))
        val page = client().getPage("/services/consultations")

        @Serializable
        data class PageFields(val title: String, val heroImage: CmsImage? = null, val subtitle: String = "none")

        @Serializable
        data class Card(val title: String, val link: CmsLink? = null)

        @Serializable
        data class CardGrid(val heading: String, val cards: List<CmsComponent> = emptyList())

        val fields = page.decodeFields<PageFields>()
        assertEquals("Book a consultation", fields.title)
        assertEquals(1600, fields.heroImage?.width)
        assertEquals("none", fields.subtitle)

        val grid = page.components.single().decodeFields<CardGrid>()
        assertEquals("Our services", grid.heading)
        assertEquals("/services/consultations", grid.cards.single().decodeFields<Card>().link?.href)
    }

    @Test
    fun `decoding the wrong shape throws a decoding error`() = runBlocking {
        answer(fixture("content.json"))
        val page = client().getPage("/")

        @Serializable
        data class Wrong(val missing: Int)

        assertThrows<CmsDecodingException> { page.decodeFields<Wrong>() }
        Unit
    }

    @Test
    fun `resolve answers with a page and sends the referrer`() = runBlocking {
        val page = CmsJson.json.parseToJsonElement(fixture("content.json")).jsonObject.getValue("data")
        answer(buildJsonObject { put("data", buildJsonObject { put("kind", "page"); put("page", page) }) }.toString())
        val result = client().resolve("/services/consultations", referrer = "https://www.example.com/")

        val request = taken()
        assertEquals("/cms/api/cdn/v1/resolve", request.url.encodedPath)
        assertEquals("https://www.example.com/", request.headers["x-cms-referrer"])
        assertInstanceOf(CmsResolution.Page::class.java, result)
        assertEquals("Consultations", (result as CmsResolution.Page).page.name)
    }

    @Test
    fun `resolve answers with redirects, gone and not found`() = runBlocking {
        answer("""{"data":{"kind":"redirect","destination":"/new","statusCode":308,"hops":2}}""")
        answer("""{"data":{"kind":"gone"}}""", 410)
        answer("""{"data":{"kind":"notFound"}}""", 404)
        val cms = client()

        assertEquals(CmsResolution.Redirect("/new", 308, permanent = true, hops = 2), cms.resolve("/old"))
        assertEquals(CmsResolution.Gone, cms.resolve("/removed"))
        assertEquals(CmsResolution.NotFound, cms.resolve("/never"))
    }

    @Test
    fun `list sends filters, sort and include`() = runBlocking {
        answer(fixture("list-fields.json"))
        val list = client { defaultRegion = "ireland" }.list(
            ListOptions(type = "faqEntry", category = "pricing", perPage = 10, sort = CmsSort.PUBLISHED_AT_DESC, includeReferences = true, includeFields = true)
                .withFilter("duration", "60")
                .withQuery("cancel"),
        )

        val url = taken().url
        assertEquals("/cms/api/cdn/v1/list", url.encodedPath)
        assertEquals("faqEntry", url.queryParameter("type"))
        assertEquals("ireland", url.queryParameter("region"))
        assertEquals("pricing", url.queryParameter("category"))
        assertEquals("10", url.queryParameter("perPage"))
        assertEquals("-publishedAt", url.queryParameter("sort"))
        assertEquals("cancel", url.queryParameter("q"))
        assertEquals("fields,references", url.queryParameter("include"))
        assertEquals("60", url.queryParameter("filter[duration]"))

        @Serializable
        data class Faq(val question: String, val answer: String)

        assertEquals(1, list.total)
        assertFalse(list.hasMore)
        assertNull(list.items.single().url)
        assertEquals("Can I cancel at any time?", list.items.single().decodeFields<Faq>().question)
    }

    @Test
    fun `navigation, global, taxonomy and products use their endpoints`() = runBlocking {
        answer("""{"data":{"key":"main","name":"Main menu","items":[{"label":"Services","href":"/services","target":null,"icon":null,"badge":null,"description":null,"children":[{"label":"Consultations","href":"/services/consultations","target":null,"icon":null,"badge":"New","children":[]}]}]}}""")
        answer("""{"data":{"key":"footer","name":"Footer","contentType":"footer","locale":"en-IE","fields":{"copyright":"© Acme Clinic"},"components":[]}}""")
        answer("""{"data":{"key":"skin concern","name":"Skin concern","terms":[{"value":"acne","label":"Acne","sortOrder":0}]}}""")
        answer("""{"data":{"items":[{"slug":"serum","url":"/shop/serum","name":"Serum","price":29.5,"currency":"EUR","wasPrice":null,"inStock":true,"onSale":false,"badges":[],"images":[],"filters":[{"taxonomy":"skin","value":"acne","label":"Acne"}],"relatedSlugs":[],"seo":null,"publishedAt":null}],"total":1,"page":1,"hasMore":false}}""")
        val cms = client()

        val menu = cms.getNavigation("main", RequestOptions(region = "ie"))
        assertEquals("New", menu.items.single().children.single().badge)
        var url = taken().url
        assertEquals("/cms/api/cdn/v1/navigation", url.encodedPath)
        assertEquals("main", url.queryParameter("key"))
        assertEquals("ie", url.queryParameter("region"))

        assertEquals("footer", cms.getGlobal("footer").key)
        assertEquals("/cms/api/cdn/v1/global", taken().url.encodedPath)

        assertEquals("Acne", cms.getTaxonomy("skin concern", locale = "en-IE").terms.single().label)
        url = taken().url
        assertEquals("/cms/api/cdn/v1/taxonomies/skin%20concern", url.encodedPath)
        assertEquals("en-IE", url.queryParameter("locale"))

        val products = cms.getProducts(page = 2, perPage = 24, filter = "acne")
        assertEquals(29.5, products.items.single().price)
        url = taken().url
        assertEquals("/cms/api/cdn/v1/products", url.encodedPath)
        assertEquals("2", url.queryParameter("page"))
        assertEquals("acne", url.queryParameter("filter"))
    }

    @Test
    fun `banners decode campaign blocks and keep unknown ones`() = runBlocking {
        answer(fixture("banners.json"))
        answer(fixture("banners.json"))
        val cms = client()

        val banners = cms.getBanners("/services/consultations", loggedIn = true)
        var url = taken().url
        assertEquals("/cms/api/cdn/v1/banners", url.encodedPath)
        assertEquals("/services/consultations", url.queryParameter("path"))
        assertEquals("true", url.queryParameter("loggedIn"))

        val banner = banners.items.single()
        assertEquals("2026-10-17T10:00:00.000Z", banners.serverTime)
        val blocks = banner.blocks
        assertEquals("Free consultations", (blocks[0] as CmsCampaignBlock.Message).segments.first().text)
        assertEquals("/book", (blocks[1] as CmsCampaignBlock.Button).link?.href)
        assertEquals("countdown", (blocks[2] as CmsCampaignBlock.Countdown).countdown.display)
        val unknown = blocks[3] as CmsCampaignBlock.Unknown
        assertEquals("sparkles", unknown.type)
        assertEquals("3", unknown.raw["intensity"]?.jsonPrimitive?.content)
        assertEquals("2026-10-20T22:59:00.000Z", banner.next?.startsAt)

        cms.getBannersForEveryPage()
        url = taken().url
        assertEquals("true", url.queryParameter("everyPage"))
        assertNull(url.queryParameter("path"))
        assertNull(url.queryParameter("loggedIn"))
    }

    @Test
    fun `popups, regions, locales, sitemaps, robots and schema`() = runBlocking {
        answer("""{"data":{"serverTime":"2026-10-17T10:00:00.000Z","items":[{"id":"p1","name":"Welcome","layout":"stacked","style":"light","minimisedText":null,"blocks":[{"id":"h","type":"heading","text":"Hello","size":"large"}],"priority":1,"hideForDays":30,"maxViews":null,"trackingLabel":null,"startsAt":null,"endsAt":null}]}}""")
        answer(fixture("regions.json"))
        answer(fixture("regions.json"))
        answer("""{"data":{"indexable":true,"sitemaps":[{"region":"ireland","locale":"en-IE","base":"https://www.example.com/ie","pages":312,"files":1}]}}""")
        answer("""{"data":{"region":"ireland","locale":"en-IE","page":1,"totalPages":1,"entries":[{"loc":"https://www.example.com/ie/","lastmod":null,"changefreq":"monthly","priority":null,"images":[],"alternates":[]}]}}""")
        answer("""{"data":{"indexable":false,"content":"User-agent: *\nDisallow: /\n"}}""")
        answer(fixture("schema.json"))
        val cms = client { defaultRegion = "ireland" }

        val popups = cms.getPopups("/")
        assertEquals("Hello", (popups.items.single().blocks.single() as CmsCampaignBlock.Heading).text)
        assertEquals("/cms/api/cdn/v1/popups", taken().url.encodedPath)

        assertEquals(2, cms.getRegions().size)
        assertEquals(emptyList<String>(), taken().url.queryParameterNames.toList())
        assertEquals(listOf("en-IE", "ga-IE"), cms.getLocales().map { it.code })
        taken()

        assertEquals(312, cms.getSitemapIndex().sitemaps.single().pages)
        assertNull(taken().url.queryParameter("region"))
        assertEquals("monthly", cms.getSitemap(page = 1).entries.single().changefreq)
        val sitemapUrl = taken().url
        assertEquals("ireland", sitemapUrl.queryParameter("region"))
        assertEquals("1", sitemapUrl.queryParameter("page"))

        assertFalse(cms.getRobots().indexable)
        assertEquals("/cms/api/cdn/v1/robots", taken().url.encodedPath)

        val schema = cms.getSchema()
        assertEquals("page", schema.contentTypes.single().key)
        assertEquals("mode", schema.components.single().fields.single().showWhen?.field)
    }

    @Test
    fun `not found throws, and find answers null`() = runBlocking {
        answer("""{"error":{"code":"not_found","message":"Content not found"}}""", 404)
        answer("""{"error":{"code":"not_found","message":"Content not found"}}""", 404)
        answer("""{"error":{"code":"not_found","message":"Navigation not found"}}""", 404)
        val cms = client()

        val error = assertThrows<CmsNotFoundException> { cms.getPage("/missing") }
        assertEquals("Content not found", error.message)
        assertEquals(404, error.status)
        assertNull(cms.findPage("/missing"))
        assertNull(cms.findNavigation("missing"))
    }

    @Test
    fun `maps errors to exceptions without retrying client errors`() = runBlocking {
        answer("""{"error":{"code":"unauthorized","message":"Invalid API key"}}""", 401)
        answer("""{"error":{"code":"rate_limited","message":"Too many requests; try again shortly"}}""", 429, "Retry-After" to "23")
        answer("""{"error":{"code":"bad_request","message":"Unknown sort"}}""", 400)
        val cms = client()

        assertEquals("Invalid API key", assertThrows<CmsUnauthorizedException> { cms.getRegions() }.message)
        assertEquals(23, assertThrows<CmsRateLimitedException> { cms.getRegions() }.retryAfter)
        val bad = assertThrows<CmsOrbitException> { cms.getRegions() }
        assertEquals("bad_request", bad.code)
        assertEquals(400, bad.status)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `rate limits without Retry-After wait a minute`() = runBlocking {
        answer("""{"error":{"code":"rate_limited","message":"Too many requests"}}""", 429)
        assertEquals(60, assertThrows<CmsRateLimitedException> { client().getRegions() }.retryAfter)
    }

    @Test
    fun `retries server errors, then succeeds`() = runBlocking {
        answer("""{"error":{"code":"internal_error","message":"The request failed"}}""", 500)
        answer("""{"error":{"code":"internal_error","message":"The request failed"}}""", 502)
        answer(fixture("regions.json"))

        assertEquals(2, client().getRegions().size)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `gives up after the retries with a server error`() = runBlocking {
        repeat(2) { answer("""{"error":{"code":"internal_error","message":"The request failed"}}""", 503) }
        val error = assertThrows<CmsServerException> { client { retries = 1 }.getRegions() }
        assertEquals(503, error.status)
        assertEquals("The request failed", error.message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `times out per attempt`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("{}").headersDelay(2, TimeUnit.SECONDS).build())
        val error = assertThrows<CmsServerException> { client { timeoutMillis = 200; retries = 0 }.getRegions() }
        assertEquals("The CMS did not answer within 200ms", error.message)
    }

    @Test
    fun `an unknown address error is still read`() = runBlocking {
        answer("""{"error":"not_found","message":"There is nothing at this address."}""", 404)
        assertEquals("There is nothing at this address.", assertThrows<CmsNotFoundException> { client().getRobots() }.message)
    }

    @Test
    fun `refuses missing keys and addresses`() {
        assertThrows<IllegalArgumentException> { CmsOrbitClient.Builder("").apply { isAndroid = false }.build() }
        assertThrows<IllegalArgumentException> { CmsOrbitClient.Builder("cms_pk_test").apply { baseUrl = " "; isAndroid = false }.build() }
    }

    @Test
    fun `refuses secret keys on Android and allows them on servers`() {
        val android = assertThrows<IllegalArgumentException> {
            CmsOrbitClient.Builder("cms_pk_test").apply { previewKey = "cms_sk_secret"; isAndroid = true }.build()
        }
        assertTrue(android.message!!.contains("Android"))
        assertThrows<IllegalArgumentException> { CmsOrbitClient.Builder("cms_sk_secret").apply { isAndroid = true }.build() }
        CmsOrbitClient.Builder("cms_sk_secret").apply { previewKey = "cms_sk_secret"; isAndroid = false }.build()
    }

    @Test
    fun `preview sends the preview key and draft`() = runBlocking {
        answer(fixture("content.json").replace("\"draft\": false", "\"draft\": true"))
        val page = client { previewKey = "cms_sk_preview" }.getPage("/", RequestOptions(preview = true))

        val request = taken()
        assertEquals("Bearer cms_sk_preview", request.headers["Authorization"])
        assertEquals("true", request.url.queryParameter("draft"))
        assertTrue(page.draft)
        assertTrue(page.meta.draft)
    }

    @Test
    fun `preview without a preview key is refused before any request`() = runBlocking {
        assertThrows<IllegalStateException> { client().getPage("/", RequestOptions(preview = true)) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `verifies preview tokens`() = runBlocking {
        answer("""{"data":{"id":"66f1","name":"Consultations","url":"/services/consultations","contentTypeKey":"page","status":"draft","draft":{"fields":{}}}}""")
        answer("""{"error":{"code":"unauthorized","message":"That preview link is invalid or has expired"}}""", 401)
        val cms = client()

        val info = cms.verifyPreviewToken("eyJ.token+/=")
        assertEquals("/services/consultations", info.url)
        val request = taken()
        assertEquals("/cms/preview", request.url.encodedPath)
        assertEquals("eyJ.token+/=", request.url.queryParameter("token"))
        assertNull(request.headers["Authorization"])

        assertThrows<CmsUnauthorizedException> { cms.verifyPreviewToken("expired") }
        Unit
    }

    @Test
    fun `graphql posts the query and answers with data`() = runBlocking {
        answer("""{"data":{"page":{"name":"Consultations"}}}""")
        answer("""{"data":null,"errors":[{"message":"Cannot query field","extensions":{"code":"GRAPHQL_VALIDATION_FAILED"}}]}""", 400)
        val cms = client()

        @Serializable
        data class Named(val name: String)

        @Serializable
        data class Answer(val page: Named?)

        val data = cms.graphqlAs<Answer>(
            "query (\$url: String!) { page(url: \$url) { name } }",
            variables = buildJsonObject { put("url", "/services/consultations") },
        )
        assertEquals("Consultations", data.page?.name)
        val request = taken()
        assertEquals("POST", request.method)
        assertEquals("/cms/api/cdn/graphql", request.url.encodedPath)
        val body = CmsJson.json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals("/services/consultations", body.getValue("variables").jsonObject["url"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("operationName"))

        val error = assertThrows<CmsOrbitException> { cms.graphql("{ nope }") }
        assertEquals("GRAPHQL_VALIDATION_FAILED", error.code)
    }

    @Test
    fun `async variants answer with futures`() {
        answer(fixture("regions.json"))
        answer("""{"error":{"code":"not_found","message":"Content not found"}}""", 404)
        val cms = client()

        assertEquals(2, cms.async.getRegions().join().size)
        assertNull(cms.async.findPage("/missing").join())
    }

    @Test
    fun `image addresses`() {
        assertEquals("https://cdn.example.com/a.jpg", CmsImages.url("https://cdn.example.com/a.jpg", 640))
        assertEquals("/cms/uploads/a.jpg?w=640&format=webp", CmsImages.url("/cms/uploads/a.jpg", 640, CmsImageFormat.WEBP))
        assertEquals("/cms/uploads/a.jpg?v=2&w=961", CmsImages.url("/cms/uploads/a.jpg?v=2", 960.6))
        assertEquals(
            "/u/uploads/a.jpg?w=640 640w, /u/uploads/a.jpg?w=960 960w, /u/uploads/a.jpg 1000w",
            CmsImages.srcSet("/u/uploads/a.jpg", 1000),
        )
        assertNull(CmsImages.srcSet("/u/uploads/a.jpg", 600))
    }
}
