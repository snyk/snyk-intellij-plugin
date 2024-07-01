package snyk.oss

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.readText
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isSnykOSSLSEnabled
import io.snyk.plugin.toLanguageServerURL
import io.snyk.plugin.toSnykFileSet
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper

class OssBulkFileListener : SnykBulkFileListener() {

    private val log = logger<OssBulkFileListener>()

    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        if (isSnykOSSLSEnabled()) {
            return
        }
        dropOssCacheIfNeeded(project, virtualFilesAffected)
    }

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        if (isSnykOSSLSEnabled()) {
            val filesAffected = toSnykFileSet(project, virtualFilesAffected)
            updateCacheAndUI(filesAffected, project)
        }
        dropOssCacheIfNeeded(project, virtualFilesAffected)
    }

    override fun forwardEvents(events: MutableList<out VFileEvent>) {
        val languageServerWrapper = LanguageServerWrapper.getInstance()

        if (!isSnykOSSLSEnabled()) return
        if (!languageServerWrapper.isInitialized) return

        val languageServer = languageServerWrapper.languageServer
        for (event in events) {
            if (event.file == null || !event.isFromSave) continue
            val file = event.file!!
            val activeProject = ProjectUtil.getActiveProject()
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                activeProject,
                "Snyk: forwarding save event to Language Server"
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val param = DidSaveTextDocumentParams(TextDocumentIdentifier(file.toLanguageServerURL()), file.readText())
                    languageServer.textDocumentService.didSave(param)
                }
            })
        }
    }

    private fun dropOssCacheIfNeeded(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        val snykCachedResults = getSnykCachedResults(project)
        if (snykCachedResults?.currentOssResults != null) {
            val buildFileChanged = virtualFilesAffected
                .filter { supportedBuildFiles.contains(it.name) }
                .find { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
            if (buildFileChanged != null) {
                snykCachedResults.currentOssResults = null
                log.debug("OSS cached results dropped due to changes in: $buildFileChanged")
            }
        }
    }

    private fun updateCacheAndUI(filesAffected: Set<SnykFile>, project: Project) {
        val cache = getSnykCachedResults(project)?.currentSnykCodeResultsLS ?: return
        filesAffected.forEach {
            cache.remove(it)
        }
        VirtualFileManager.getInstance().asyncRefresh()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    companion object {
        // see https://github.com/snyk/snyk/blob/master/src/lib/detect.ts#L10
        private val supportedBuildFiles = listOf(
            "yarn.lock",
            "package-lock.json",
            "package.json",
            "Gemfile",
            "Gemfile.lock",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "build.sbt",
            "Pipfile",
            "requirements.txt",
            "Gopkg.lock",
            "go.mod",
            "vendor.json",
            "project.assets.json",
            "project.assets.json",
            "packages.config",
            "paket.dependencies",
            "composer.lock",
            "Podfile",
            "Podfile.lock",
            "pyproject.toml",
            "poetry.lock",
            ".snyk"
        )
    }
}
