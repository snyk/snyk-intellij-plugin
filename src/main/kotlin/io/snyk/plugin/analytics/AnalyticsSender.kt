package io.snyk.plugin.analytics

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.analytics.AbstractAnalyticsEvent
import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedQueue

class AnalyticsSender(val project: Project) : Disposable {
    private var disposed: Boolean = false

    // left = event, right = callback function
    private val eventQueue = ConcurrentLinkedQueue<Pair<AbstractAnalyticsEvent, () -> Unit>>()

    init {
        Disposer.register(SnykPluginDisposable.getInstance(project), this)
        start()
    }

    private fun start() {
        runAsync {
            val lsw = LanguageServerWrapper.getInstance(project)
            while (!disposed) {
                if (eventQueue.isEmpty() || lsw.notAuthenticated()) {
                    Thread.sleep(1000)
                    continue
                }
                val copyForSending = LinkedList(eventQueue)
                for (event in copyForSending) {
                    try {
                        lsw.sendReportAnalyticsCommand(event.first)
                        event.second()
                    } catch (e: Exception) {
                        lsw.logger.warn("unexpected exception while sending analytics")
                    } finally {
                        eventQueue.remove(event)
                    }
                }
            }
        }
    }

    fun logEvent(event: AbstractAnalyticsEvent, callback: () -> Unit = {}) = eventQueue.add(Pair(event, callback))

    companion object {
        private var instance: AnalyticsSender? = null

        @JvmStatic
        fun getInstance(project: Project): AnalyticsSender {
            if (instance == null) {
                instance = AnalyticsSender(project)
            }
            return instance as AnalyticsSender
        }
    }

    override fun dispose() {
        this.disposed = true
    }
}
