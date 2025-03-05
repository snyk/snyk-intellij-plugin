package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.lsp.AiFixParams
interface SnykShowIssueDetailListener {
    companion object {
        val SHOW_ISSUE_DETAIL_TOPIC =
            Topic.create("Snyk Show Issue Detail LS", SnykShowIssueDetailListener::class.java)

        const val SNYK_URI_SCHEME = "snyk"
        const val SNYK_CODE_PRODUCT = "Snyk Code"
        const val SHOW_DETAIL_ACTION = "showInDetailPanel"
    }

    fun onShowIssueDetail(aiFixParams: AiFixParams)
}
