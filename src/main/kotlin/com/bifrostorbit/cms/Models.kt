package com.bifrostorbit.cms

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** An image or file from an Asset or Assets field. */
@Serializable
public data class CmsImage(
    val id: String,
    val url: String,
    val mimeType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val alt: String = "",
    val title: String? = null,
    val subtitles: String? = null,
    val focalPoint: CmsFocalPoint? = null,
)

/** The point to keep in view when an image is cut to fit, from 0 to 1 across and down. */
@Serializable
public data class CmsFocalPoint(val x: Double, val y: Double)

/** A picture of a trip or destination from Travel Orbit. */
@Serializable
public data class CmsTravelImage(val url: String, val alt: String = "")

/** One departure of a Travel Orbit trip. Prices are in minor units, such as cents. */
@Serializable
public data class CmsTripDeparture(
    val id: String,
    val startDate: String,
    val endDate: String? = null,
    val status: String = "open",
    val placesLeft: Int? = null,
    val guaranteed: Boolean = false,
    val priceFrom: Long? = null,
    val currency: String = "",
    val bookingUrl: String? = null,
)

/** A Trip or Trips field: a trip from Travel Orbit with its live price and next departures. */
@Serializable
public data class CmsTrip(
    val id: String,
    val slug: String? = null,
    val name: String,
    val summary: String = "",
    val image: CmsTravelImage? = null,
    val days: Int? = null,
    val priceFrom: Long? = null,
    val currency: String? = null,
    val pricesFrom: Map<String, Long> = emptyMap(),
    val tripCurrency: String? = null,
    val url: String? = null,
    val nextDepartures: List<CmsTripDeparture> = emptyList(),
    val guaranteed: Boolean = false,
    val available: Boolean = false,
    val images: List<CmsTravelImage> = emptyList(),
    val activities: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val areas: List<String> = emptyList(),
    val difficulty: String? = null,
    val currencies: List<String> = emptyList(),
    val bookingMode: String? = null,
    val details: CmsTripDetails? = null,
)

/** The trip's full description, itinerary and guides, on the page that picks it. */
@Serializable
public data class CmsTripDetails(
    val description: String = "",
    val highlights: List<String> = emptyList(),
    val included: List<String> = emptyList(),
    val notIncluded: List<String> = emptyList(),
    val itinerary: List<Day> = emptyList(),
    val guides: List<Guide> = emptyList(),
) {
    @Serializable
    public data class Day(val id: String, val title: String, val body: String = "", val distanceKm: Double? = null, val location: Location? = null, val stay: String? = null)

    @Serializable
    public data class Location(val lat: Double, val lng: Double)

    @Serializable
    public data class Guide(val name: String, val role: String = "", val bio: String = "", val photo: CmsTravelImage? = null, val languages: List<String> = emptyList())
}

/** A Destination field: a country or continent from Travel Orbit. */
@Serializable
public data class CmsDestination(
    val code: String,
    val name: String,
    val kind: String,
    val tagline: String? = null,
    val image: CmsTravelImage? = null,
    val tripCount: Int? = null,
    val url: String? = null,
)

/** A Departure field: one date of a Travel Orbit trip. */
@Serializable
public data class CmsDeparture(
    val id: String,
    val startDate: String,
    val endDate: String? = null,
    val status: String = "closed",
    val placesLeft: Int? = null,
    val guaranteed: Boolean = false,
    val priceFrom: Long? = null,
    val currency: String = "",
    val bookingUrl: String? = null,
    val tripId: String,
    val tripName: String,
    val tripUrl: String? = null,
)

/** A Link field. [href] is the address to use, already worked out for links to pages and files. */
@Serializable
public data class CmsLink(
    val kind: String,
    val href: String? = null,
    val contentId: String? = null,
    val assetId: String? = null,
    val label: String? = null,
    val target: String? = null,
    val travel: CmsTravelLink? = null,
)

/** The trip (its id), destination (its country code or continent, with [kind]) or guide (their address name) a link points at. */
@Serializable
public data class CmsTravelLink(
    val id: String,
    val name: String,
    val kind: String? = null,
)

/** An entry a Reference or References field points at, when read with `includeReferences`. */
@Serializable
public data class CmsEntry(
    val id: String,
    val url: String? = null,
    val name: String,
    val contentType: String,
    val publishedAt: String? = null,
    val fields: JsonObject = JsonObject(emptyMap()),
) {
    public inline fun <reified T> decodeFields(): T = CmsJson.decode(fields)
    public fun <T> decodeFields(serializer: KSerializer<T>): T = CmsJson.decode(serializer, fields)
}

/**
 * A block on a page: one placed component. Blocks in a slot arrive both in [children], under the slot's key,
 * and on the slot field in [fields].
 */
@Serializable
public data class CmsComponent(
    val id: String,
    val type: String,
    val schemaVersion: Int = 1,
    val fields: JsonObject = JsonObject(emptyMap()),
    val children: Map<String, List<CmsComponent>> = emptyMap(),
    val variant: String? = null,
) {
    public inline fun <reified T> decodeFields(): T = CmsJson.decode(fields)
    public fun <T> decodeFields(serializer: KSerializer<T>): T = CmsJson.decode(serializer, fields)
}

/** Another name for [CmsComponent]. */
public typealias CmsBlock = CmsComponent

@Serializable
public data class CmsImageSummary(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val alt: String = "",
)

@Serializable
public data class CmsRobotsDirective(val index: Boolean = true, val follow: Boolean = true)

@Serializable
public data class CmsOpenGraph(
    val type: String = "website",
    val title: String = "",
    val description: String = "",
    val url: String = "",
    val siteName: String = "",
    val locale: String = "",
    val image: CmsImageSummary? = null,
)

@Serializable
public data class CmsTwitterCard(
    val card: String = "summary_large_image",
    val site: String? = null,
    val title: String = "",
    val description: String = "",
    val image: CmsImageSummary? = null,
)

@Serializable
public data class CmsVerification(val google: String? = null, val bing: String? = null)

@Serializable
public data class CmsAlternate(val hreflang: String, val href: String)

@Serializable
public data class CmsBreadcrumb(val name: String, val url: String)

/** Ready-made metadata for a page. */
@Serializable
public data class CmsSeo(
    val title: String = "",
    val description: String = "",
    val canonical: String = "",
    val robots: CmsRobotsDirective = CmsRobotsDirective(),
    val openGraph: CmsOpenGraph = CmsOpenGraph(),
    val twitter: CmsTwitterCard = CmsTwitterCard(),
    val verification: CmsVerification = CmsVerification(),
    val alternates: List<CmsAlternate> = emptyList(),
    val breadcrumbs: List<CmsBreadcrumb> = emptyList(),
    val jsonLd: List<JsonObject> = emptyList(),
)

/** Which region and language answered, and whether a fallback was used. */
@Serializable
public data class CmsPageMeta(
    val region: String,
    val locale: String,
    val requestedRegion: String,
    val requestedLocale: String,
    val fallbackApplied: Boolean,
    val regionFallbackApplied: Boolean,
    val draft: Boolean,
    val references: Boolean = false,
)

@Serializable
public data class CmsExpired(val message: String)

/** The Insights Orbit site a page's space is connected to, with the [site] key for the script. */
@Serializable
public data class CmsInsightsSite(val site: String)

/** A page, as `/content` and `/resolve` deliver it. [id] and [insights] are `null` from CMS Orbit versions that do not send them. */
@Serializable
public data class CmsPage(
    val url: String,
    val name: String,
    val contentType: String,
    val locale: String,
    val region: String,
    val requestedLocale: String = locale,
    val requestedRegion: String = region,
    val fallbackApplied: Boolean = false,
    val regionFallbackApplied: Boolean = false,
    val draft: Boolean = false,
    val publishedAt: String? = null,
    val version: Int = 0,
    val fields: JsonObject = JsonObject(emptyMap()),
    val components: List<CmsComponent> = emptyList(),
    val seo: CmsSeo = CmsSeo(),
    val expired: CmsExpired? = null,
    @Transient val meta: CmsPageMeta = CmsPageMeta(
        region = region,
        locale = locale,
        requestedRegion = requestedRegion,
        requestedLocale = requestedLocale,
        fallbackApplied = fallbackApplied,
        regionFallbackApplied = regionFallbackApplied,
        draft = draft,
    ),
    // [EXPLAIN-D270]
    val id: String? = null,
    val insights: CmsInsightsSite? = null,
) {
    public inline fun <reified T> decodeFields(): T = CmsJson.decode(fields)
    public fun <T> decodeFields(serializer: KSerializer<T>): T = CmsJson.decode(serializer, fields)
}

/** What an address is: a page, a redirect, gone or not found. */
public sealed class CmsResolution {
    public data class Page(val page: CmsPage) : CmsResolution()
    public data class Redirect(val to: String, val status: Int, val permanent: Boolean, val hops: Int) : CmsResolution()
    public data object Gone : CmsResolution()
    public data object NotFound : CmsResolution()
}

@Serializable
public data class CmsNavItem(
    val label: String,
    val href: String? = null,
    val target: String? = null,
    val icon: String? = null,
    val badge: String? = null,
    val description: String? = null,
    val children: List<CmsNavItem> = emptyList(),
)

@Serializable
public data class CmsNavigation(val key: String, val name: String, val items: List<CmsNavItem> = emptyList())

@Serializable
public data class CmsGlobal(
    val key: String,
    val name: String,
    val contentType: String,
    val locale: String,
    val fields: JsonObject = JsonObject(emptyMap()),
    val components: List<CmsComponent> = emptyList(),
) {
    public inline fun <reified T> decodeFields(): T = CmsJson.decode(fields)
    public fun <T> decodeFields(serializer: KSerializer<T>): T = CmsJson.decode(serializer, fields)
}

/** An entry in a list. [fields] is only set when the list was read with `includeFields` or `includeReferences`. */
@Serializable
public data class CmsContentSummary(
    val id: String,
    val url: String? = null,
    val name: String,
    val contentType: String,
    val publishedAt: String? = null,
    val seo: JsonObject? = null,
    val fields: JsonObject? = null,
) {
    public inline fun <reified T> decodeFields(): T =
        CmsJson.decode(fields ?: throw CmsDecodingException("This list was read without its fields; set includeFields"))

    public fun <T> decodeFields(serializer: KSerializer<T>): T =
        CmsJson.decode(serializer, fields ?: throw CmsDecodingException("This list was read without its fields; set includeFields"))
}

@Serializable
public data class CmsContentList(
    val items: List<CmsContentSummary> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val hasMore: Boolean = false,
)

@Serializable
public data class CmsLocale(val code: String, val name: String, val isDefault: Boolean = false)

@Serializable
public data class CmsRegion(val key: String, val name: String, val locales: List<CmsLocale> = emptyList())

@Serializable
public data class CmsSitemapFile(
    val region: String,
    val locale: String,
    val base: String? = null,
    val pages: Int = 0,
    val files: Int = 0,
)

@Serializable
public data class CmsSitemapIndex(val indexable: Boolean, val sitemaps: List<CmsSitemapFile> = emptyList())

@Serializable
public data class CmsSitemapImage(val loc: String, val title: String? = null)

@Serializable
public data class CmsSitemapEntry(
    val loc: String,
    val lastmod: String? = null,
    val changefreq: String? = null,
    val priority: Double? = null,
    val images: List<CmsSitemapImage> = emptyList(),
    val alternates: List<CmsAlternate> = emptyList(),
)

@Serializable
public data class CmsSitemap(
    val region: String,
    val locale: String,
    val page: Int = 1,
    val totalPages: Int = 1,
    val entries: List<CmsSitemapEntry> = emptyList(),
)

@Serializable
public data class CmsRobots(val indexable: Boolean, val content: String)

@Serializable
public data class CmsSchemaOption(val label: String, val value: String)

/** `equals` is a string, a boolean or a list of strings. */
@Serializable
public data class CmsShowWhen(val field: String, val equals: JsonElement)

@Serializable
public data class CmsSchemaField(
    val key: String,
    val name: String,
    val type: String,
    val required: Boolean = false,
    val description: String? = null,
    val options: List<CmsSchemaOption>? = null,
    val allowedComponents: List<String>? = null,
    val allowedContentTypes: List<String>? = null,
    val taxonomy: String? = null,
    val validation: JsonObject? = null,
    val showWhen: CmsShowWhen? = null,
)

@Serializable
public data class CmsSchemaContentType(
    val key: String,
    val name: String,
    val description: String? = null,
    val isGlobal: Boolean = false,
    val fields: List<CmsSchemaField> = emptyList(),
)

/** [allowedChildren] is a list of component keys, or the string `"*"` for any. */
@Serializable
public data class CmsSchemaComponent(
    val key: String,
    val name: String,
    val description: String? = null,
    val group: String? = null,
    val allowedChildren: JsonElement? = null,
    val fields: List<CmsSchemaField> = emptyList(),
)

@Serializable
public data class CmsSchema(
    val contentTypes: List<CmsSchemaContentType> = emptyList(),
    val components: List<CmsSchemaComponent> = emptyList(),
)

@Serializable
public data class CmsFollowUp(val text: String, val link: CmsLink? = null)

/** A countdown, worked out for the moment of the request. [display] is `countdown`, `hidden`, `followUp` or `ended`. */
@Serializable
public data class CmsCountdown(
    val label: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
    val timeZone: String = "UTC",
    val style: String = "",
    val icon: String? = null,
    val button: CmsLink? = null,
    val status: String = "running",
    val display: String = "countdown",
    val followUp: CmsFollowUp? = null,
    val endedText: String? = null,
)

@Serializable
public data class CmsTextSegment(val text: String, val highlight: Boolean = false)

@Serializable
public data class CmsPopup(
    val id: String,
    val name: String,
    val layout: String,
    val style: String,
    val minimisedText: String? = null,
    val blocks: List<CmsCampaignBlock> = emptyList(),
    val priority: Int = 0,
    val hideForDays: Int = 30,
    val maxViews: Int? = null,
    val trackingLabel: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
)

/** A banner. [placement] is `aboveHeader` or `belowHeader`. [next] takes over when this one ends, from its [startsAt]. */
@Serializable
public data class CmsBanner(
    val id: String,
    val blocks: List<CmsCampaignBlock> = emptyList(),
    val text: String = "",
    val segments: List<CmsTextSegment> = emptyList(),
    val link: CmsLink? = null,
    val background: String = "",
    val foreground: String = "",
    val placement: String = "aboveHeader",
    val paths: List<String> = emptyList(),
    val pinned: Boolean = false,
    val priority: Int = 0,
    val trackingLabel: String? = null,
    val countdown: CmsCountdown? = null,
    val endsAt: String? = null,
    val startsAt: String? = null,
    val next: CmsBanner? = null,
)

/** Items worked out for the moment of the request, with the CMS clock in [serverTime]. */
@Serializable
public data class CmsTimed<T>(val serverTime: String, val items: List<T> = emptyList())

@Serializable
public data class CmsProductFilter(val taxonomy: String? = null, val value: String, val label: String)

@Serializable
public data class CmsProduct(
    val slug: String,
    val url: String? = null,
    val name: String,
    val catalogId: String? = null,
    val size: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val wasPrice: Double? = null,
    val inStock: Boolean? = null,
    val onSale: Boolean = false,
    val badges: List<String> = emptyList(),
    val benefits: String? = null,
    val directions: String? = null,
    val ingredients: String? = null,
    val terms: String? = null,
    val images: List<CmsImageSummary> = emptyList(),
    val filters: List<CmsProductFilter> = emptyList(),
    val relatedSlugs: List<String> = emptyList(),
    val seo: CmsSeo? = null,
    val publishedAt: String? = null,
)

@Serializable
public data class CmsProductPage(
    val items: List<CmsProduct> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val hasMore: Boolean = false,
)

@Serializable
public data class CmsTaxonomyTerm(val value: String, val label: String, val sortOrder: Int = 0)

@Serializable
public data class CmsTaxonomy(val key: String, val name: String, val terms: List<CmsTaxonomyTerm> = emptyList())

/** The entry a preview token is for. */
@Serializable
public data class CmsPreviewInfo(
    val id: String,
    val name: String,
    val url: String? = null,
    val contentTypeKey: String,
    val status: String,
)
