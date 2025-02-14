package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.lsp.AiFixParams
interface SnykAiFixListener {
    companion object {
        val AI_FIX_TOPIC = Topic.create("Snyk Ai Fix LS", SnykAiFixListener::class.java)
    }

    fun onAiFix(aiFixParams: AiFixParams)
}
