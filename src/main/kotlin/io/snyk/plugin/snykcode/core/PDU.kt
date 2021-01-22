package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.snykcode.SnykCodeNotifications
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import java.util.function.Consumer

class PDU private constructor() : PlatformDependentUtilsBase() {

    override fun getProject(file: Any): Any = toPsiFile(file).project

    override fun getProjectName(project: Any): String = toProject(project).name

    override fun getFileName(file: Any): String = toPsiFile(file).virtualFile.name

    override fun getProjectBasedFilePath(file: Any): String {
        val psiFile = toPsiFile(file)
        // looks like we don't need ReadAction for this (?)
        var absolutePath = psiFile.virtualFile.path
        val projectPath = psiFile.project.basePath
        if (projectPath != null) {
            absolutePath = absolutePath.replace(projectPath, "")
        }
        return absolutePath
    }

    override fun getOpenProjects(): Array<Any> = ProjectManager.getInstance().openProjects as Array<Any>

    override fun getFileSize(file: Any): Long = toPsiFile(file).virtualFile.length

    override fun getLineStartOffset(file: Any, line: Int): Int {
        val psiFile = toPsiFile(file)
        val document = RunUtils.computeInReadActionInSmartMode(
            psiFile.project, Computable { psiFile.viewProvider.document })
        if (document == null) {
            SCLogger.instance.logWarn("Document not found for file: $psiFile")
            return 0
        }
        if (document.lineCount <= line) {
            SCLogger.instance.logWarn("Line $line is out of Document.lineCount=${document.lineCount}")
            return 0
        }
        return document.getLineStartOffset(line)
    }

    override fun runInBackgroundCancellable(file: Any, title: String, progressConsumer: Consumer<Any>) =
        RunUtils.instance.runInBackgroundCancellable(file, title, progressConsumer)

    override fun runInBackground(project: Any, title: String, progressConsumer: Consumer<Any>) =
        RunUtils.instance.runInBackground(project, title, progressConsumer)

    override fun cancelRunningIndicators(project: Any) = RunUtils.instance.cancelRunningIndicators(project)

    override fun doFullRescan(project: Any) {
        val runUtils = RunUtils.instance
        if (!runUtils.isFullRescanRequested(project)) {
            runUtils.rescanInBackgroundCancellableDelayed(project, DEFAULT_DELAY_SMALL, false)
        }
    }

    override fun refreshPanel(project: Any) {
        // not yet needed
    }

    override fun isLogged(project: Any?, userActionNeeded: Boolean): Boolean = LoginUtils.instance
        .isLogged(project, userActionNeeded)

    override fun progressSetText(progress: Any?, text: String?) {
        if (progress is ProgressIndicator) {
            progress.text = text
        }
    }

    override fun progressCheckCanceled(progress: Any?) {
        if (progress is ProgressIndicator) {
            progress.checkCanceled()
        }
    }

    override fun progressSetFraction(progress: Any?, fraction: Double) {
        if (progress is ProgressIndicator) {
            progress.fraction = fraction
        }
    }

    override fun showInBrowser(url: String) {
        BrowserUtil.open(SnykCodeParams.instance.loginUrl)
    }

    override fun showLoginLink(project: Any?, message: String) {
        showError(message, project)
        runForProject(project, Consumer { prj ->
            ApplicationManager.getApplication().invokeLater {
                prj.service<SnykToolWindowPanel>().displayAuthPanel()
            }
        })
    }

    override fun showConsentRequest(project: Any?, userActionNeeded: Boolean) {
        //TODO("Not yet implemented")
    }

    override fun showInfo(message: String, project: Any?) {
        runForProject(project, Consumer { prj -> SnykCodeNotifications.showInfo(message, prj) })
    }

    override fun showWarn(message: String, project: Any?) {
        runForProject(project, Consumer { prj -> SnykCodeNotifications.showWarn(message, prj) })
    }

    override fun showError(message: String, project: Any?) {
        runForProject(project, Consumer { prj -> SnykCodeNotifications.showError(message, prj) })
    }

    private fun runForProject(project: Any?, function: Consumer<Project>) {
        if (project != null) {
            function.accept(toProject(project))
        } else for (prj in openProjects) {
            function.accept(toProject(prj))
        }
    }

    companion object {
        fun toPsiFile(file: Any): PsiFile {
            require(file is PsiFile) { "file should be PsiFile instance" }
            return file
        }

        fun toPsiFiles(files: Collection<Any>): Collection<PsiFile> {
            return files.map { toPsiFile(it) }
        }

        fun toProject(project: Any): Project {
            require(project is Project) { "project should be Project instance" }
            return project
        }

        fun toTextRange(myTextRange: MyTextRange): TextRange {
            return TextRange(myTextRange.start, myTextRange.end)
        }

        val instance = PDU()
    }
}
