# CMS Orbit SDK for Kotlin

The Kotlin SDK, `com.bifrostorbit:cms-sdk`, reads published content from the [delivery API](https://docs.bifrostorbit.com/cms/developers/delivery-api/) in Android apps and on JVM servers, such as Ktor or Spring Boot. It does what the client in the [JavaScript SDK](https://docs.bifrostorbit.com/cms/developers/sdk/) does: pages, redirects, menus, site-wide content, lists, banners, popups, products, sitemaps and the schema, with the same retries and errors. It is open source under the MIT licence.

- Suspend functions for Kotlin coroutines, and `CompletableFuture`s for Java.
- Built on OkHttp and kotlinx.serialization. Bring your own `OkHttpClient` to share its connection pool and interceptors.
- Every answer has a `@Serializable` model. Fields arrive as a `JsonObject`, and you decode them into your own types when you need them.

## Install

It needs Java 11 or later on servers, and Android 5.0 (API 21) or later in apps. The Java futures need Android 7.0 (API 24). Kotlin projects need Kotlin 2.2 or later.

The package is [`com.bifrostorbit:cms-sdk` on Maven Central](https://central.sonatype.com/artifact/com.bifrostorbit/cms-sdk), where you can see the latest version.

```kotlin
// build.gradle.kts
plugins {
    kotlin("plugin.serialization") version "2.4.21" // to decode fields into your own types
}

dependencies {
    implementation("com.bifrostorbit:cms-sdk:0.2.0")
}
```

With Maven:

```xml
<dependency>
  <groupId>com.bifrostorbit</groupId>
  <artifactId>cms-sdk</artifactId>
  <version>0.2.0</version>
</dependency>
```

An Android app also needs `<uses-permission android:name="android.permission.INTERNET" />` in its manifest.

## Set up

Create a public key in **Configuration › API keys**. It starts with `cms_pk_` and reads published content only, so it may ship in an app.

```kotlin
import com.bifrostorbit.cms.CmsOrbitClient

val cms = CmsOrbitClient("cms_pk_...") {
    defaultRegion = "germany"
}
```

In Java:

```java
CmsOrbitClient cms = CmsOrbitClient.builder("cms_pk_...")
    .defaultRegion("germany")
    .build();
```

| Option | What it does |
| --- | --- |
| `apiKey` | A public key (`cms_pk_…`). Required. |
| `baseUrl` | The API address. `https://api.bifrostorbit.com/cms` by default. |
| `environment(…)` | `PRODUCTION` or `STAGING`, a shortcut for `baseUrl`. Staging is `https://api-stage.bifrostorbit.com/cms`. |
| `previewKey` | A secret key (`cms_sk_…`) for drafts. Servers only: refused in Android apps. |
| `defaultRegion` | Used when a call names no region. |
| `defaultLocale` | Used when a call names no language. Without it, each region answers in its own default language. |
| `timeoutMillis` | Per attempt. 5 seconds by default. |
| `retries` | Extra attempts after a network error or a server error. 2 by default. |
| `okHttpClient` | Your own OkHttp client, for interceptors, logging, a proxy or certificate pinning. |

Create one client and share it. It is safe to use from many coroutines and threads at once.

### Secret keys

A secret key reads drafts, so anyone who has it can read unpublished content. The SDK refuses a `cms_sk_` key, as `apiKey` or `previewKey`, when it runs on Android, because an app's files can be read by anyone who installs it. This matches the JavaScript SDK, which refuses secret keys in browser code. On a JVM server a secret key is allowed: keep it in an environment variable or a secrets manager, never in source control.

## Methods

Every method is a suspend function. Call it from a coroutine, such as `viewModelScope.launch` on Android or a Ktor route.

```kotlin
val result = cms.resolve("/gear/fjell-gtx-boot")
val page = cms.getPage("/gear/fjell-gtx-boot", RequestOptions(locale = "de-DE"))
val header = cms.getNavigation("header")
val footer = cms.getGlobal("footer")
val articles = cms.list(ListOptions(type = "article", sort = CmsSort.PUBLISHED_AT_DESC, perPage = 12))
```

| Method | Answers with |
| --- | --- |
| `resolve(path, options, referrer)` | `CmsResolution`: `Page`, `Redirect`, `Gone` or `NotFound`. Call it before showing a page. |
| `getPage(path, options)`, `findPage(…)` | One page by address. `find…` answers `null` instead of throwing when nothing is published there. |
| `list(ListOptions)` | Entries of one content type, filtered and paged, with their fields when asked |
| `getGlobal(key, options)`, `findGlobal(…)` | Site-wide content such as a footer, by its key |
| `getNavigation(key, options)`, `findNavigation(…)` | A menu, by its key |
| `getBanners(path, loggedIn, options)` | Banners live now for a page |
| `getBannersForEveryPage(loggedIn, options)` | Banners live now on any page, each with the `paths` it targets |
| `getPopups(path, loggedIn, options)` | Popups live now for a page |
| `getRegions()`, `getLocales(region)` | The regions and languages the key may read |
| `getSitemapIndex()`, `getSitemap(region, locale, page)`, `getRobots()` | Sitemaps and robots rules |
| `getProducts(page, perPage, filter, options)`, `getTaxonomy(key, locale)` | Shop products with live prices, and taxonomy terms |
| `graphql(query, variables, operationName, preview)` | Any [GraphQL](https://docs.bifrostorbit.com/cms/developers/graphql/) query, as a `JsonElement`. `graphqlAs<T>(…)` decodes it. |
| `getSchema()` | Content types and components |
| `verifyPreviewToken(token)` | The entry a preview link is for. See [Preview](#preview). |

`RequestOptions` holds `region`, `locale`, `preview`, `strict` and `includeReferences`. `ListOptions` adds `type`, `page`, `perPage`, `filter`, `tag`, `category`, `sort`, `q` and `includeFields`. From Java, build them with `new RequestOptions().withRegion("de")` and `new ListOptions("article").withPerPage(12)`.

A page has `meta`, which says which region and language answered and whether a fallback was used, so an app can show a notice such as "This page is not available in English yet."

### From Java

`cms.getAsync()` has the same methods, each answering with a `CompletableFuture`. Call `join()` to wait for the answer on a server, or chain `thenAccept` to stay off the calling thread. Cancelling a future cancels its request.

```java
CmsResolution result = cms.getAsync().resolve("/gear/fjell-gtx-boot").join();
if (result instanceof CmsResolution.Page) {
    CmsPage page = ((CmsResolution.Page) result).getPage();
    System.out.println(page.getName());
} else if (result instanceof CmsResolution.Redirect) {
    response.sendRedirect(((CmsResolution.Redirect) result).getTo());
}
```

A failed call completes the future with a `CompletionException` from `join()`, whose `getCause()` is one of the SDK's exceptions.

## Your own types

Fields arrive as a `JsonObject`. Declare a `@Serializable` class with the fields you use, and decode them with `decodeFields`. Fields you leave out are ignored, and missing or `null` values take your defaults.

```kotlin
@Serializable
data class FaqEntry(val question: String, val answer: String = "")

val faqs = cms.list(ListOptions(type = "faqEntry", category = "pricing", includeFields = true))
val entries = faqs.items.map { it.decodeFields<FaqEntry>() }
```

`decodeFields` is on pages, blocks, global entries, list items and referenced entries. Images decode as `CmsImage` and links as `CmsLink`. `CmsJson.decode<T>(element)` decodes any other `JsonElement`. A shape that does not match throws `CmsDecodingException`.

From Java, pass the JSON to your own library: `page.getFields().toString()` is the fields as JSON text, ready for Jackson or Gson.

## Components in Jetpack Compose

A page's `components` is a list of `CmsComponent`, each with `id`, `type`, `fields`, `children` and `variant`. Decode each into your own class and draw it:

```kotlin
@Serializable
data class Hero(val heading: String, val image: CmsImage? = null, val description: String = "")

@Composable
fun CmsBlocks(blocks: List<CmsComponent>) {
    Column {
        blocks.forEach { block ->
            when (block.type) {
                "hero" -> HeroView(block.decodeFields<Hero>())
                "card-grid" -> CardGridView(block)
                else -> Unit
            }
        }
    }
}

@Composable
fun HeroView(hero: Hero) {
    Column {
        hero.image?.let {
            AsyncImage(model = CmsImages.url(it.url, width = 1080), contentDescription = it.alt)
        }
        Text(hero.heading, style = MaterialTheme.typography.headlineLarge)
        Text(AnnotatedString.fromHtml(hero.description))
    }
}
```

Load the page in a view model, so it survives screen rotation:

```kotlin
class PageViewModel(private val cms: CmsOrbitClient) : ViewModel() {
    var result by mutableStateOf<CmsResolution?>(null)
        private set

    fun load(path: String) = viewModelScope.launch {
        result = try {
            cms.resolve(path)
        } catch (error: CmsOrbitException) {
            null
        }
    }
}
```

- `AsyncImage` is from [Coil](https://coil-kt.github.io/coil/). `CmsImages.url` asks the CMS for an image resized to the width you show it at, and `CmsImages.widths` lists the sizes on offer.
- Rich text fields are HTML. `AnnotatedString.fromHtml` in Compose 1.7 shows headings, bold, italics and links; for tables and embeds, use a `WebView`.
- Blocks in a slot are on the slot's field and in `children`, as in [Render components](https://docs.bifrostorbit.com/cms/developers/render-blocks/). Decode a Components field as `List<CmsComponent>` and pass it to `CmsBlocks` again.
- An unknown block type draws nothing, so an app that is not yet updated keeps working when editors add a component.

## Insights Orbit

Pages carry `id`, the entry's id, and `insights`, a `CmsInsightsSite` when the space is connected to Insights Orbit, both `null` otherwise. On a server that renders HTML, `CmsInsights.tags(page)` writes the script with the site key and `<meta name="insights-content">` with the entry's id, or an empty string without a site key:

```kotlin
val head = CmsInsights.tags(page, src = "/stats/script.js", api = "/stats/collect")
```

It takes `site`, `contentId`, `src`, `api`, `disable`, `campaigns` and `waitForConsent`, like `CmsInsights` in the JavaScript SDK.

## Preview

Drafts need a secret key, which an Android app may not hold. Preview drafts on a JVM server instead, such as the server that renders your website.

1. Give the client a `previewKey` with the secret key.
2. When the editor opens your website with `?preview=…`, check the token with `verifyPreviewToken`. It answers with the entry, including its `url`, or throws `CmsUnauthorizedException` when the token is invalid or has expired.
3. Remember that the visitor is previewing, such as in a session cookie, and read pages with `RequestOptions(preview = true)`.

```kotlin
get("/api/preview") {
    val token = call.parameters["preview"] ?: return@get call.respond(HttpStatusCode.BadRequest)
    val entry = try {
        cms.verifyPreviewToken(token)
    } catch (error: CmsUnauthorizedException) {
        return@get call.respond(HttpStatusCode.Unauthorized)
    }
    call.sessions.set(PreviewSession(enabled = true))
    call.respondRedirect(entry.url ?: "/")
}
```

Only redirect to addresses on your own website. Asking for a preview without a `previewKey` throws an `IllegalStateException` before any request is sent. See [Set up the live preview](https://docs.bifrostorbit.com/cms/developers/preview/).

## Webhooks

[Publish webhooks](https://docs.bifrostorbit.com/cms/developers/webhooks/) are signed. On a JVM server, `CmsWebhook.parse` checks the signature and the timestamp and answers the message, or `null` when it is not genuine or is more than five minutes old. Pass the raw body, before any JSON parsing. With Ktor:

```kotlin
post("/api/cms-webhook") {
    val message = CmsWebhook.parse(
        System.getenv("CMS_WEBHOOK_SECRET"),
        call.request.headers[CmsWebhook.TIMESTAMP_HEADER].orEmpty(),
        call.request.headers[CmsWebhook.SIGNATURE_HEADER].orEmpty(),
        call.receive<ByteArray>(),
    ) ?: return@post call.respond(HttpStatusCode.Unauthorized)
    call.respond(HttpStatusCode.NoContent)
    if (message.event != "webhook.test") pageCache.invalidateAll()
}
```

`CmsWebhook.verify(secret, timestamp, signature, body)` answers only `true` or `false`. Both take the body as a `String` or a `ByteArray`, use the whole secret, including `whsec_`, and throw `IllegalArgumentException` when the secret is empty. From Java they are static: `CmsWebhook.verify(secret, timestamp, signature, body)`.

## Errors

Failed calls throw a `CmsOrbitException`, or one of its kinds. Each has the HTTP `status` and the API's `code`.

| Exception | When |
| --- | --- |
| `CmsNotFoundException` | Nothing published at the address, or no menu, global entry, taxonomy, region or language with that key |
| `CmsUnauthorizedException` | A missing, unknown, expired or refused key, a public key asking for drafts, or a region or language the key may not read |
| `CmsSubscriptionInactiveException` | The organisation's CMS Orbit trial or plan has ended, so delivery is paused (`402`). Not retried. |
| `CmsRateLimitedException` | Too many requests. `retryAfter` is the number of seconds to wait. |
| `CmsServerException` | The CMS failed, could not be reached or did not answer in time, after every retry |
| `CmsDecodingException` | The answer, or your own type, could not be decoded |
| `CmsOrbitException` | Anything else, such as `400` with the code `bad_request` |

Network errors and server errors are retried twice with back-off, and each attempt times out after 5 seconds. Client errors are never retried. Cancelling the coroutine cancels the request.

When your organisation's CMS Orbit trial or plan has ended, delivery is paused: every call throws `CmsSubscriptionInactiveException` (status `402`, code `subscription_inactive`) straight away, without retrying, whatever code the API sends. Catch it, keep showing what the app or website already has, and show a calm "temporarily unavailable" message where there is nothing. Nothing is deleted, and content comes back as soon as an owner chooses a plan.

## Caching

The client does not cache answers. The API's `Cache-Control` is meant for shared caches such as a CDN (`s-maxage`), so OkHttp's own cache does not keep them; see Caching in the [delivery API](https://docs.bifrostorbit.com/cms/developers/delivery-api/). In an app, keep what you show in memory or in a local database, and read it again when the app returns to the foreground. On a server, keep answers for about a minute, or refresh them when [publish webhooks](https://docs.bifrostorbit.com/cms/developers/webhooks/) arrive.

## Images

`CmsImages.url(url, width, format)` gives the address of a CMS upload resized to `width` pixels and converted to `CmsImageFormat.WEBP` or `AVIF`. `CmsImages.widths(url, width)` lists the widths to offer, and `CmsImages.srcSet(url, width)` writes an HTML `srcset` for server-rendered pages. Addresses that are not CMS uploads are returned as they are.

## Generated types

`generateKotlin(schema, { packageName, includeReferences })` in `packages/cms-sdk/src/cli/generate-kotlin.ts` writes a `@Serializable` data class for every component (`HeroBlock`) and content type (`PageFields`) from the answer of `getSchema()`, the same schema the TypeScript generator reads. It also writes a `Block` sealed interface, `CmsComponent.toBlock()`, which decodes a block into its component's class, and `BlockTypes` with every component key, so a Compose renderer can use `when (val block = component.toBlock())`.

- Required fields are never `null`, and every property has a default, so drafts with empty fields still decode.
- Keys that are not Kotlin identifiers, such as `hero-image`, get `@SerialName`.
- Reference fields are ids, or `CmsEntry` with `includeReferences: true`.

It is not yet wired into the `cms-orbit` command line.

## Develop

The project builds with the Gradle wrapper, so only a JDK is needed (17 or later to run Gradle; the library itself is compiled for Java 11).

```bash
./gradlew test       # JUnit 5 and MockWebServer
./gradlew build      # compiles, tests and assembles build/libs
```

The tests cover request addresses, query parameters and headers, decoding from fixtures in `src/test/resources/fixtures`, every error, retries and timeouts, key checks, preview and the Java futures.

## Publish to Maven Central

Releases go to Maven Central through the [Central Portal](https://central.sonatype.com) with the [vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/), which is already set up in `build.gradle.kts`. The POM details are in `gradle.properties`.

One-off setup:

1. Create an account at [central.sonatype.com](https://central.sonatype.com) and register the namespace `com.bifrostorbit`. Prove you own the domain by adding the TXT record the portal gives you to `bifrostorbit.com` in Cloudflare.
2. Under **Account › Generate User Token**, create a token. It has a user name and a password.
3. Create a GPG key for signing, and publish its public part:

   ```bash
   gpg --full-generate-key                       # RSA 4096, no expiry or a long one
   gpg --list-secret-keys --keyid-format short   # note the 8-character key id
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   gpg --export-secret-keys --armor <KEY_ID>     # the private key, for the next step
   ```

4. Put the credentials in `~/.gradle/gradle.properties`, never in this repository, or in CI secrets as `ORG_GRADLE_PROJECT_<name>` environment variables:

   ```properties
   mavenCentralUsername=<token user name>
   mavenCentralPassword=<token password>
   signingInMemoryKey=<the armoured private key, on one line with \n for line breaks>
   signingInMemoryKeyPassword=<its passphrase>
   ```

Each release:

1. Set `VERSION_NAME` in `gradle.properties`.
2. Run `./gradlew publishAndReleaseToMavenCentral`. Use `publishToMavenCentral` instead to upload first and release by hand under **Deployments** in the portal.
3. The version appears on Maven Central within about 30 minutes.

Signing only runs when a signing key is configured, so `./gradlew publishToMavenLocal` works without one for trying a build in another project.
