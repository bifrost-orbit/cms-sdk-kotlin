package com.bifrostorbit.cms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CompletableFuture

/**
 * The calls of [CmsOrbitClient] for Java, each answering with a `CompletableFuture`. Call `join()` on it to wait
 * for the answer. Cancelling a future cancels its request. Get it from [CmsOrbitClient.async].
 */
public class CmsOrbitAsyncClient internal constructor(private val client: CmsOrbitClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun <T> run(block: suspend CmsOrbitClient.() -> T): CompletableFuture<T> = scope.future { client.block() }

    @JvmOverloads
    public fun getPage(path: String, options: RequestOptions = RequestOptions.DEFAULT): CompletableFuture<CmsPage> =
        run { getPage(path, options) }

    @JvmOverloads
    public fun findPage(path: String, options: RequestOptions = RequestOptions.DEFAULT): CompletableFuture<CmsPage?> =
        run { findPage(path, options) }

    @JvmOverloads
    public fun resolve(
        path: String,
        options: RequestOptions = RequestOptions.DEFAULT,
        referrer: String? = null,
    ): CompletableFuture<CmsResolution> = run { resolve(path, options, referrer) }

    @JvmOverloads
    public fun getNavigation(key: String, options: RequestOptions = RequestOptions.DEFAULT): CompletableFuture<CmsNavigation> =
        run { getNavigation(key, options) }

    @JvmOverloads
    public fun findNavigation(key: String, options: RequestOptions = RequestOptions.DEFAULT): CompletableFuture<CmsNavigation?> =
        run { findNavigation(key, options) }

    @JvmOverloads
    public fun getGlobal(key: String, options: RequestOptions = RequestOptions.DEFAULT): CompletableFuture<CmsGlobal> =
        run { getGlobal(key, options) }

    @JvmOverloads
    public fun findGlobal(key: String, options: RequestOptions = RequestOptions.DEFAULT): CompletableFuture<CmsGlobal?> =
        run { findGlobal(key, options) }

    public fun list(options: ListOptions): CompletableFuture<CmsContentList> = run { list(options) }

    public fun getRegions(): CompletableFuture<List<CmsRegion>> = run { getRegions() }

    @JvmOverloads
    public fun getLocales(region: String? = null): CompletableFuture<List<CmsLocale>> = run { getLocales(region) }

    public fun getSitemapIndex(): CompletableFuture<CmsSitemapIndex> = run { getSitemapIndex() }

    @JvmOverloads
    public fun getSitemap(region: String? = null, locale: String? = null, page: Int? = null): CompletableFuture<CmsSitemap> =
        run { getSitemap(region, locale, page) }

    public fun getRobots(): CompletableFuture<CmsRobots> = run { getRobots() }

    @JvmOverloads
    public fun getPopups(
        path: String,
        loggedIn: Boolean = false,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CompletableFuture<CmsTimed<CmsPopup>> = run { getPopups(path, loggedIn, options) }

    @JvmOverloads
    public fun getBanners(
        path: String,
        loggedIn: Boolean = false,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CompletableFuture<CmsTimed<CmsBanner>> = run { getBanners(path, loggedIn, options) }

    @JvmOverloads
    public fun getBannersForEveryPage(
        loggedIn: Boolean = false,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CompletableFuture<CmsTimed<CmsBanner>> = run { getBannersForEveryPage(loggedIn, options) }

    @JvmOverloads
    public fun getProducts(
        page: Int? = null,
        perPage: Int? = null,
        filter: String? = null,
        options: RequestOptions = RequestOptions.DEFAULT,
    ): CompletableFuture<CmsProductPage> = run { getProducts(page, perPage, filter, options) }

    @JvmOverloads
    public fun getTaxonomy(key: String, locale: String? = null): CompletableFuture<CmsTaxonomy> = run { getTaxonomy(key, locale) }

    public fun getSchema(): CompletableFuture<CmsSchema> = run { getSchema() }

    @JvmOverloads
    public fun graphql(
        query: String,
        variables: JsonObject? = null,
        operationName: String? = null,
        preview: Boolean = false,
    ): CompletableFuture<JsonElement?> = run { graphql(query, variables, operationName, preview) }

    public fun verifyPreviewToken(token: String): CompletableFuture<CmsPreviewInfo> = run { verifyPreviewToken(token) }
}
