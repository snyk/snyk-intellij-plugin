package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.RunUtilsBase
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.snykcode.SnykCodeResults
import java.util.function.Consumer

class RunUtils private constructor() : RunUtilsBase(
    PDU.instance,
    HashContentUtils.instance,
    AnalysisData.instance,
    SnykCodeUtils.instance,
    SCLogger.instance
) {

    override fun reuseCurrentProgress(project: Any, title: String, progressConsumer: Consumer<Any>): Boolean {
        val progressManager = ProgressManager.getInstance()
        val progressIndicator = progressManager.progressIndicator
        if (getRunningProgresses(project).contains(progressIndicator)) {
            val myBackgroundable = MyBackgroundable(PDU.toProject(project), title, progressConsumer)
            progressManager.runProcessWithProgressAsynchronously(myBackgroundable, progressIndicator)
            return true
        }
        return false
    }

    override fun doBackgroundRun(project: Any, title: String, progressConsumer: Consumer<Any>) {
        ProgressManager.getInstance()
            .run(MyBackgroundable(PDU.toProject(project), title, progressConsumer))
    }

    private fun toProgress(progress: Any): ProgressIndicator {
        require(progress is ProgressIndicator) { "progress should be ProgressIndicator instance" }
        return progress
    }

    override fun cancelProgress(progress: Any) {
        toProgress(progress).cancel()
    }

    override fun bulkModeForceUnset(project: Any) {
        // probably don't needed
    }

    override fun bulkModeUnset(project: Any) {
        // probably don't needed
    }

    override fun updateAnalysisResultsUIPresentation(files: Collection<Any>) {
        if (files.isEmpty()) return
        val project = PDU.toPsiFile(files.first()).project
        val cliScanPublisher =
            project.messageBus.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC)
        val scanResults: SnykCodeResults = SnykCodeResults(
            AnalysisData.instance.getAnalysis(files).mapKeys { PDU.toPsiFile(it.key) }
        )
/*
        val psiManager = PsiManager.getInstance(project)
        val psiFile = RunUtils.computeInReadActionInSmartMode(
            project,
            Computable { project.projectFile?.let { psiManager.findFile(it) }!! })
            ?: return SnykCodeResults()
        return SnykCodeResults(mapOf(
            Pair(
                psiFile,
                listOf(SuggestionForFile("1", "", "Fake suggestion", 1, emptyList()))
            )))
*/
        cliScanPublisher.scanningSnykCodeFinished(scanResults)
    }

    private class MyBackgroundable(project: Project, title: String, private val consumer: Consumer<Any>) :
        Backgroundable(project, "${SCLogger.presentableName}: $title") {

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            consumer.accept(indicator)
        }
    }

    companion object {
        val instance = RunUtils()

        fun <T> computeInReadActionInSmartMode(
            project: Project, computation: Computable<T>): T? {
            val dumbService = ReadAction.compute<DumbService?, RuntimeException> {
                if (project.isDisposed) null else DumbService.getInstance(project)
            }
            if (dumbService == null) {
                //            DCLogger.getInstance().logWarn("dumbService == null")
                return null
            }
            return dumbService.runReadActionInSmartMode(computation)
        }
    }

}
