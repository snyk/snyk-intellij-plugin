package snyk.common

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import io.ktor.util.collections.ConcurrentMap
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.lsp.LsProduct
import snyk.common.lsp.PresentableError
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

@Service(Service.Level.PROJECT)
class SnykCachedResults(val project: Project) : Disposable {
  private var disposed = false
    get() {
      return project.isDisposed || ApplicationManager.getApplication().isDisposed || field
    }

  init {
    Disposer.register(SnykPluginDisposable.getInstance(project), this)
  }

  override fun dispose() {
    disposed = true
    clearCaches()
  }

  fun isDisposed() = disposed

  val currentSnykCodeResultsLS: MutableMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()
  val currentOSSResultsLS: ConcurrentMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()
  val currentIacResultsLS: MutableMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()
  val currentSecretsResultsLS: MutableMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()

  var currentOssError: PresentableError? = null
  var currentIacError: PresentableError? = null
  var currentSnykCodeError: PresentableError? = null
  var currentSecretsError: PresentableError? = null

  // Debouncing for annotation refresh - coalesces per-file diagnostic updates
  private val annotationRefreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  internal val pendingAnnotationRefreshFiles: MutableSet<VirtualFile> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

  fun clearCaches() {
    currentOssError = null
    currentIacError = null
    currentSnykCodeError = null
    currentSecretsError = null

    currentSnykCodeResultsLS.clear()
    currentOSSResultsLS.clear()
    currentIacResultsLS.clear()
    currentSecretsResultsLS.clear()
  }

  fun initCacheUpdater() {
    project.messageBus
      .connect()
      .subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        object : SnykScanListener {
          val logger = logger<SnykCachedResults>()

          override fun scanningStarted(snykScan: SnykScanParams) {
            logger.info("scanningStarted for project ${project.name}")
            currentOssError = null
            currentSnykCodeError = null
            currentIacError = null
            currentSecretsError = null
          }

          override fun scanningSnykCodeFinished() = Unit

          override fun scanningOssFinished() = Unit

          override fun scanningIacFinished() = Unit

          override fun scanningError(snykScan: SnykScanParams) {
            when (LsProduct.getFor(snykScan.product)) {
              LsProduct.OpenSource -> {
                currentOSSResultsLS.clear()
                currentOssError = snykScan.presentableError
              }
              LsProduct.Code -> {
                currentSnykCodeResultsLS.clear()
                currentSnykCodeError = snykScan.presentableError
              }
              LsProduct.InfrastructureAsCode -> {
                currentIacResultsLS.clear()
                currentIacError = snykScan.presentableError
              }
              LsProduct.Secrets -> {
                currentSecretsResultsLS.clear()
                currentSecretsError = snykScan.presentableError
              }
              LsProduct.Unknown -> Unit
            }

            val errorMessage =
              snykScan.presentableError?.error
                ?: "Scanning error for project ${project.name}. Data: $snykScan"
            if (snykScan.presentableError?.showNotification == true) {
              SnykBalloonNotificationHelper.showError(errorMessage, project)
            }
          }

          override fun onPublishDiagnostics(
            product: LsProduct,
            snykFile: SnykFile,
            issues: Set<ScanIssue>,
          ) {
            if (snykFile.project.isDisposed || !snykFile.isInContent()) return
            when (product) {
              LsProduct.OpenSource -> currentOSSResultsLS[snykFile] = issues
              LsProduct.Code -> currentSnykCodeResultsLS[snykFile] = issues
              LsProduct.InfrastructureAsCode -> currentIacResultsLS[snykFile] = issues
              LsProduct.Secrets -> {
                currentSecretsResultsLS[snykFile] = issues
              }
              LsProduct.Unknown -> return
            }
            // Schedule debounced annotation refresh - coalesces rapid per-file updates
            scheduleAnnotationRefresh(snykFile.virtualFile)
          }
        },
      )
  }

  /**
   * Schedules a debounced annotation refresh for the given file. Collects files over a short window
   * and then refreshes them in batch.
   */
  private fun scheduleAnnotationRefresh(virtualFile: VirtualFile) {
    pendingAnnotationRefreshFiles.add(virtualFile)
    annotationRefreshAlarm.cancelAllRequests()
    annotationRefreshAlarm.addRequest(
      { flushPendingAnnotationRefreshes() },
      ANNOTATION_REFRESH_DEBOUNCE_MS,
    )
  }

  /** Flushes all pending annotation refreshes, either individually or as a global refresh. */
  private fun flushPendingAnnotationRefreshes() {
    if (isDisposed() || project.isDisposed) return

    val filesToRefresh = drainPendingAnnotationRefreshFiles()

    if (filesToRefresh.isEmpty()) return

    // Invalidate code vision for all affected files
    invokeLater {
      if (!project.isDisposed) {
        project
          .service<CodeVisionHost>()
          .invalidateProvider(CodeVisionHost.LensInvalidateSignal(null))
      }
    }

    // Batch all refreshes into a single invokeLater to avoid EDT queue flooding
    invokeLater {
      if (isDisposed() || project.isDisposed) return@invokeLater
      val analyzer = DaemonCodeAnalyzer.getInstance(project)

      if (filesToRefresh.size > MAX_INDIVIDUAL_ANNOTATION_REFRESH) {
        // Too many files - do a global refresh
        analyzer.restart()
      } else {
        // Refresh each file individually within same EDT task
        filesToRefresh.forEach { file ->
          if (file.isValid) {
            PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
              analyzer.restart(psiFile)
            }
          }
        }
      }
    }
  }

  /**
   * Atomically drains [pendingAnnotationRefreshFiles] using [MutableSet.removeIf]. Each element is
   * removed and collected in a single per-element atomic step, eliminating the race that existed
   * with the previous `toList().also { clear() }` pattern: a file added between `toList()` and
   * `clear()` would have been silently dropped. With [MutableSet.removeIf] any file added after the
   * drain has passed its bucket remains in the set and is picked up on the next flush cycle.
   */
  internal fun drainPendingAnnotationRefreshFiles(): List<VirtualFile> {
    val filesToRefresh = mutableListOf<VirtualFile>()
    pendingAnnotationRefreshFiles.removeIf { filesToRefresh.add(it) }
    return filesToRefresh
  }

  companion object {
    // Debounce delay for annotation refresh - allows collecting multiple files
    private const val ANNOTATION_REFRESH_DEBOUNCE_MS = 150

    // Max files to refresh individually before falling back to global refresh
    private const val MAX_INDIVIDUAL_ANNOTATION_REFRESH = 20
  }
}

internal class SnykFileIssueComparator(private val snykResults: Map<SnykFile, List<ScanIssue>>) :
  Comparator<SnykFile> {
  override fun compare(o1: SnykFile, o2: SnykFile): Int {
    val files = o1.virtualFile.path.compareTo(o2.virtualFile.path)
    val o1Criticals = getCount(o1, Severity.CRITICAL)
    val o2Criticals = getCount(o2, Severity.CRITICAL)
    val o1Errors = getCount(o1, Severity.HIGH)
    val o2Errors = getCount(o2, Severity.HIGH)
    val o1Warningss = getCount(o1, Severity.MEDIUM)
    val o2Warningss = getCount(o2, Severity.MEDIUM)
    val o1Infos = getCount(o1, Severity.LOW)
    val o2Infos = getCount(o2, Severity.LOW)

    return when {
      o1Criticals != o2Criticals -> o2Criticals - o1Criticals
      o1Errors != o2Errors -> o2Errors - o1Errors
      o1Warningss != o2Warningss -> o2Warningss - o1Warningss
      o1Infos != o2Infos -> o2Infos - o1Infos
      else -> files
    }
  }

  private fun getCount(file: SnykFile, severity: Severity) =
    snykResults[file]?.filter { it.getSeverityAsEnum() == severity }?.size ?: 0
}
