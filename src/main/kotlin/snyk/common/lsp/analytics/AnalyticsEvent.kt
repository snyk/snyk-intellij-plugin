package snyk.common.lsp.analytics

data class AnalyticsEvent(
    val interactionType: String,
    val category: List<String>,
    val status: String = "success",
    val targetId: String = "pkg:filesystem/scrubbed",
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val results: Map<String, Any> = emptyMap(),
    val errors: List<Any> = emptyList(),
    val extension: Map<String, Any> = emptyMap(),
) : AbstractAnalyticsEvent
