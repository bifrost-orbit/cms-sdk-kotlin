package com.bifrostorbit.cms

import kotlin.math.roundToInt

/** Image formats the CMS can convert uploads to. */
public enum class CmsImageFormat(internal val value: String) { WEBP("webp"), AVIF("avif") }

/** Image addresses for CMS uploads, resized and converted on the fly. */
public object CmsImages {
    private val srcSetWidths = listOf(320, 480, 640, 768, 960, 1280, 1600, 1920)

    /** The address of [url] resized to [width] pixels and converted to [format]. Addresses outside CMS uploads are returned as they are. */
    @JvmStatic
    @JvmOverloads
    public fun url(url: String, width: Number? = null, format: CmsImageFormat? = null): String {
        if (!url.contains("/uploads/")) return url
        val path = url.substringBefore('?')
        val params = LinkedHashMap<String, String>()
        url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach {
            params[it.substringBefore('=')] = it.substringAfter('=', "")
        }
        if (width != null) params["w"] = width.toDouble().roundToInt().toString()
        if (format != null) params["format"] = format.value
        if (params.isEmpty()) return path
        return path + "?" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    /** Widths to offer for an image of [width] pixels, smallest first: for an Android image loader or an HTML `srcset`. */
    @JvmStatic
    public fun widths(url: String, width: Int?): List<Int> {
        if (!url.contains("/uploads/")) return emptyList()
        return srcSetWidths.filter { width == null || it < width }
    }

    /** An HTML `srcset` value, or `null` when the image is not a CMS upload or is smaller than every width. */
    @JvmStatic
    public fun srcSet(url: String, width: Int?): String? {
        val widths = widths(url, width)
        if (widths.isEmpty()) return null
        val entries = widths.map { "${url(url, it)} ${it}w" }.toMutableList()
        if (width != null) entries.add("$url ${width}w")
        return entries.joinToString(", ")
    }
}
