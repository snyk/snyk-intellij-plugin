package snyk.common.lsp

import com.google.common.cache.CacheBuilder
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.util.TestRuntimeUtil
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isInContent
import io.snyk.plugin.toLanguageServerURI
import io.snyk.plugin.toSnykFileSet
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.io.File
import java.time.Duration
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.runAsync

class LanguageServerBulkFileListener(project: Project) : SnykBulkFileListener(project) {
  @TestOnly var disabled = TestRuntimeUtil.isRunningUnderUnitTest

  override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) = Unit

  override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
    if (disabled) return
    if (virtualFilesAffected.isEmpty()) return

    runAsync {
      if (project.isDisposed) return@runAsync
      if (DumbService.getInstance(project).isDumb) return@runAsync

      val languageServerWrapper = LanguageServerWrapper.getInstance(project)
      if (languageServerWrapper.isDisposed() || !languageServerWrapper.isInitialized) {
        return@runAsync
      }

      val languageServer = languageServerWrapper.languageServer
      val cache = getSnykCachedResults(project)?.currentSnykCodeResultsLS
      val filesAffected = toSnykFileSet(project, virtualFilesAffected)

      for (file in filesAffected) {
        val virtualFile = file.virtualFile
        if (!shouldProcess(virtualFile, project)) {
          continue
        }
        cache?.remove(file)
        val param =
          DidSaveTextDocumentParams(
            TextDocumentIdentifier(virtualFile.toLanguageServerURI()),
            virtualFile.readText(),
          )
        languageServer.textDocumentService.didSave(param)
      }
      updateCacheAndUI(filesAffected, project)
    }
  }

  // Cache for debouncing file updates that come in within one second of the last
  // Key = path, Value is irrelevant
  private val debounceFileCache =
    CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMillis(1000)).build<String, Boolean>()

  private val blackListedDirectories = setOf(".idea", ".git", ".hg", ".svn")

  private fun shouldProcess(file: VirtualFile, project: Project): Boolean {
    if (project.isDisposed) return false
    if (DumbService.getInstance(project).isDumb) return false

    // Check debounce cache first (no read action needed)
    val inCache = debounceFileCache.getIfPresent(file.path)
    if (inCache != null) {
      return false
    }
    debounceFileCache.put(file.path, true)

    // Check if file is in content (isInContent handles its own read action)
    if (!file.isInContent(project)) return false
    if (isInBlacklistedParentDir(file)) return false

    return true
  }

  private fun isInBlacklistedParentDir(file: VirtualFile): Boolean {
    val path =
      file.path.split(File.separatorChar).filter {
        blackListedDirectories.contains(it.trimEnd(File.separatorChar))
      }
    return path.isNotEmpty()
  }

  private fun updateCacheAndUI(filesAffected: Set<SnykFile>, project: Project) {
    val ossCache = getSnykCachedResults(project)?.currentOSSResultsLS ?: return
    val codeCache = getSnykCachedResults(project)?.currentSnykCodeResultsLS ?: return
    val iacCache = getSnykCachedResults(project)?.currentIacResultsLS ?: return

    filesAffected.forEach {
      ossCache.remove(it)
      codeCache.remove(it)
      iacCache.remove(it)
    }

    // Note: Avoid VirtualFileManager.asyncRefresh() as it refreshes ALL files including
    // remote/HTTP files, which can cause NPE in RemoteFileInfoImpl when localFile is null.
    // The DaemonCodeAnalyzer restart below handles annotation updates for open files.
    invokeLater {
      if (project.isDisposed || SnykPluginDisposable.getInstance(project).isDisposed()) {
        return@invokeLater
      }
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
  }
}
