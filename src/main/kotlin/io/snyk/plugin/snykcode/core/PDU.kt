package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.SnykError
import java.util.function.Consumer

class PDU private constructor() : PlatformDependentUtilsBase() {

    override fun getProject(file: Any): Any {
        return toSnykCodeFile(file).project
    }

    override fun getProjectName(project: Any): String = toProject(project).name

    override fun getFileName(file: Any): String = toVirtualFile(file).name

    override fun getFilePath(file: Any): String {
        return toVirtualFile(file).path
    }

    override fun getDirPath(file: Any): String = toVirtualFile(file).let {
        it.parent?.path ?: it.path // root dir case
    }

    override fun getProjectBasedFilePath(file: Any): String {
        val snykCodeFile = toSnykCodeFile(file)
        val virtualFile = snykCodeFile.virtualFile
        val absolutePath = virtualFile.path
        val projectPath = snykCodeFile.project.guessProjectDir()?.path
            ?: throw IllegalStateException("No Project base Path found for file $file")
        // `ignoreCase = true` needed due to https://youtrack.jetbrains.com/issue/IDEA-268081
        return absolutePath.replaceFirst(projectPath, "", ignoreCase = true)
    }

    override fun getFileByDeepcodedPath(path: String, project: Any): Any? {
        val prj = toProject(project)
        val absolutePath = prj.guessProjectDir()?.path + if (path.startsWith("/")) path else "/$path"
        val virtualFile =
            LocalFileSystem.getInstance().findFileByPath(absolutePath)
                ?: LocalFileSystem.getInstance().findFileByPath(path)

        if (virtualFile == null) {
            SCLogger.instance.logWarn("VirtualFile not found for: $absolutePath or $path")
            return null
        }
        return SnykCodeFile(prj, virtualFile)
    }

    override fun getOpenProjects(): Array<Any> = ProjectManager.getInstance().openProjects.toList().toTypedArray()

    override fun getFileSize(file: Any): Long = toVirtualFile(file).length

    override fun getLineStartOffset(file: Any, line: Int): Int {
        val psiFile = toPsiFile(file) ?: return 0
        val document =
            RunUtils.computeInReadActionInSmartMode(psiFile.project, Computable { psiFile.viewProvider.document })
        if (document == null) {
            SCLogger.instance.logWarn("Document not found for file: $psiFile")
            return 0
        }
        if (line < 0 || document.lineCount <= line) {
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

    /**
     * _Is not doing Login (token validity) check_ due to lack of proxying `/session` api
     * Instead will be called from AnalysisDataBase.isNotSucceed() only when 401 status code already happened
     */
    override fun isLogged(project: Any?, userActionNeeded: Boolean): Boolean {
        showLoginLink(project, "<ignored>")
        return false
    }

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

    override fun progressCanceled(progress: Any?): Boolean = progress is ProgressIndicator && progress.isCanceled

    override fun progressSetFraction(progress: Any?, fraction: Double) {
        if (progress is ProgressIndicator) {
            progress.fraction = fraction
        }
    }

    override fun showInBrowser(url: String) {
        BrowserUtil.open(SnykCodeParams.instance.loginUrl)
    }

    override fun showLoginLink(project: Any?, message: String) {
        pluginSettings().token = null
        runForProject(project, Consumer { prj ->
            ApplicationManager.getApplication().invokeLater {
                getSnykToolWindowPanel(prj)?.displayAuthPanel()
            }
        })
    }

    override fun showConsentRequest(project: Any?, userActionNeeded: Boolean) {
        //TODO("Not yet implemented")
    }

    override fun showInfo(message: String, project: Any?) {
        runForProject(project, Consumer { prj -> SnykBalloonNotificationHelper.showInfo(message, prj) })
    }

    override fun showWarn(message: String, project: Any?, wasWarnShown: Boolean) {
        // wasWarnShown will be needed for auto scans only
        runForProject(project, Consumer { prj ->
            SnykBalloonNotificationHelper.showWarn(message, prj)
            getSyncPublisher(prj, SnykScanListener.SNYK_SCAN_TOPIC)?.scanningSnykCodeError(
                SnykError(message, prj.basePath ?: "")
            )
        })
    }

    override fun showError(message: String, project: Any?) {
        runForProject(project, Consumer { prj ->
            SnykBalloonNotificationHelper.showError(message, prj)
            getSyncPublisher(prj, SnykScanListener.SNYK_SCAN_TOPIC)?.scanningSnykCodeError(
                SnykError(message, prj.basePath ?: "")
            )
        })
    }

    private fun runForProject(project: Any?, function: Consumer<Project>) {
        if (project != null) {
            function.accept(toProject(project))
        } else for (prj in openProjects) {
            function.accept(toProject(prj))
        }
    }

    companion object {

        fun toPsiFile(file: Any): PsiFile? {
            val snykCodeFile = toSnykCodeFile(file)
            return RunUtils.computeInReadActionInSmartMode(
                snykCodeFile.project,
                Computable {
                    return@Computable if (snykCodeFile.virtualFile.isValid) {
                        PsiManager.getInstance(snykCodeFile.project).findFile(snykCodeFile.virtualFile)
                    } else null
                }
            )
        }

        fun toVirtualFile(file: Any): VirtualFile = toSnykCodeFile(file).virtualFile

        fun toSnykCodeFile(file: Any): SnykCodeFile {
            require(file is SnykCodeFile) { "File $file must be of type SnykCodeFile but is ${file.javaClass.name}" }
            return file
        }

        fun toProject(project: Any): Project {
            require(project is Project) { "project $project should be Project instance" }
            return project
        }

        fun toTextRange(myTextRange: MyTextRange): TextRange {
            return TextRange(myTextRange.start, myTextRange.end)
        }

        val instance = PDU()
    }
}
