package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.lsp.AiFixParams
interface SnykShowIssueDetailListener {
    companion object {
        val SHOW_ISSUE_DETAIL_TOPIC = Topic.create(
            "Snyk Show Issue Detail LS",
            SnykShowIssueDetailListener::class.java
        )
    }

    fun onShowIssueDetail(aiFixParams: AiFixParams)
}
