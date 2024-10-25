package snyk.common.lsp.analytics

import io.snyk.plugin.getPluginPath

data class AnalyticsEvent(
    val interactionType: String,
    val category: List<String>,
    val status: String = "success",
    val targetId: String = "pkg:file/${getPluginPath()}",
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val results: Map<String, Any> = emptyMap(),
    val errors: List<Any> = emptyList(),
    val extension: Map<String, Any> = emptyMap(),
) : AbstractAnalyticsEvent
