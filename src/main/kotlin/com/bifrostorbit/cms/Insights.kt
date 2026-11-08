package com.bifrostorbit.cms

/** The Insights Orbit script for server-rendered HTML, with the same output as the JavaScript SDK's `CmsInsights`. */
public object CmsInsights {
    public const val SCRIPT: String = "https://api.bifrostorbit.com/insights/v1/script.js"

    // [EXPLAIN-D271]
    /**
     * The tags for `<head>`: `<meta name="insights-content">` with the page's entry id and the script with the site key.
     * An empty string without a site key, from [site] or the page's [CmsPage.insights].
     */
    @JvmStatic
    @JvmOverloads
    public fun tags(
        page: CmsPage? = null,
        site: String? = null,
        contentId: String? = null,
        src: String = SCRIPT,
        api: String? = null,
        disable: List<String> = emptyList(),
        campaigns: Boolean = true,
        waitForConsent: Boolean = false,
    ): String {
        val key = site?.takeIf { it.isNotEmpty() } ?: page?.insights?.site?.takeIf { it.isNotEmpty() } ?: return ""
        val script = buildString {
            append("<script defer data-site=\"").append(escape(key)).append('"')
            if (!api.isNullOrEmpty()) append(" data-api=\"").append(escape(api)).append('"')
            if (disable.isNotEmpty()) append(" data-disable=\"").append(escape(disable.joinToString(" "))).append('"')
            if (campaigns) append(" data-campaigns")
            if (waitForConsent) append(" data-consent=\"wait\"")
            append(" src=\"").append(escape(src)).append("\"></script>")
        }
        val entry = contentId ?: page?.id
        return if (entry.isNullOrEmpty()) script else "<meta name=\"insights-content\" content=\"${escape(entry)}\">\n$script"
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("'", "&#039;").replace("<", "&lt;").replace(">", "&gt;")
}
