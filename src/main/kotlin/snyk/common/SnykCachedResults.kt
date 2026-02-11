package snyk.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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

  var currentOssError: PresentableError? = null
  var currentIacError: PresentableError? = null
  var currentSnykCodeError: PresentableError? = null

  fun clearCaches() {
    currentOssError = null
    currentIacError = null
    currentSnykCodeError = null

    currentSnykCodeResultsLS.clear()
    currentOSSResultsLS.clear()
    currentIacResultsLS.clear()
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
              LsProduct.Unknown -> Unit
            }
          }
        },
      )
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
