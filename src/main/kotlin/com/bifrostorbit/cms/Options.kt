package com.bifrostorbit.cms

/**
 * Options most calls take.
 *
 * @property region Region key or short code, such as `germany` or `de`. Defaults to the client's `defaultRegion`.
 * @property locale Language code, such as `de-DE`. Defaults to the client's `defaultLocale`, then the region's own language.
 * @property preview Read drafts with the client's preview key.
 * @property strict Refuse every fallback: the content must exist in the language and region asked for.
 * @property includeReferences Replace Reference and References fields with the entries they point at.
 */
public data class RequestOptions @JvmOverloads constructor(
    val region: String? = null,
    val locale: String? = null,
    val preview: Boolean = false,
    val strict: Boolean = false,
    val includeReferences: Boolean = false,
) {
    public fun withRegion(region: String?): RequestOptions = copy(region = region)
    public fun withLocale(locale: String?): RequestOptions = copy(locale = locale)
    public fun withPreview(preview: Boolean): RequestOptions = copy(preview = preview)
    public fun withStrict(strict: Boolean): RequestOptions = copy(strict = strict)
    public fun withIncludeReferences(includeReferences: Boolean): RequestOptions = copy(includeReferences = includeReferences)

    public companion object {
        @JvmField
        public val DEFAULT: RequestOptions = RequestOptions()
    }
}

/** The orders a list can be sorted in. */
public enum class CmsSort(public val value: String) {
    SORT_ORDER("sortOrder"),
    NAME("name"),
    NAME_DESC("-name"),
    PUBLISHED_AT("publishedAt"),
    PUBLISHED_AT_DESC("-publishedAt"),
    UPDATED_AT("updatedAt"),
    UPDATED_AT_DESC("-updatedAt"),
}

/**
 * Options for [CmsOrbitClient.list].
 *
 * @property type The content type key, such as `product`.
 * @property perPage 1 to 100. 20 when not set.
 * @property filter Exact matches on fields, sent as `filter[<field>]=<value>`.
 * @property q Entries whose name contains this text, ignoring case.
 * @property includeFields Add each entry's fields.
 * @property includeReferences Add each entry's fields, with references replaced by the entries they point at.
 */
public data class ListOptions @JvmOverloads constructor(
    val type: String,
    val region: String? = null,
    val locale: String? = null,
    val preview: Boolean = false,
    val page: Int? = null,
    val perPage: Int? = null,
    val filter: Map<String, String> = emptyMap(),
    val tag: String? = null,
    val category: String? = null,
    val sort: CmsSort? = null,
    val q: String? = null,
    val includeFields: Boolean = false,
    val includeReferences: Boolean = false,
) {
    public fun withRegion(region: String?): ListOptions = copy(region = region)
    public fun withLocale(locale: String?): ListOptions = copy(locale = locale)
    public fun withPreview(preview: Boolean): ListOptions = copy(preview = preview)
    public fun withPage(page: Int?): ListOptions = copy(page = page)
    public fun withPerPage(perPage: Int?): ListOptions = copy(perPage = perPage)
    public fun withFilter(field: String, value: String): ListOptions = copy(filter = filter + (field to value))
    public fun withTag(tag: String?): ListOptions = copy(tag = tag)
    public fun withCategory(category: String?): ListOptions = copy(category = category)
    public fun withSort(sort: CmsSort?): ListOptions = copy(sort = sort)
    public fun withQuery(q: String?): ListOptions = copy(q = q)
    public fun withIncludeFields(includeFields: Boolean): ListOptions = copy(includeFields = includeFields)
    public fun withIncludeReferences(includeReferences: Boolean): ListOptions = copy(includeReferences = includeReferences)
}
