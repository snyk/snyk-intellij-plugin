package snyk.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams
import snyk.container.ContainerResult
import snyk.container.ContainerService
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service(Service.Level.PROJECT)
class SnykCachedResults(val project: Project) {

    val currentSnykCodeResultsLS: MutableMap<SnykFile, List<ScanIssue>> = mutableMapOf()

    val currentOSSResultsLS: MutableMap<SnykFile, List<ScanIssue>> = mutableMapOf()

    var currentOssResults: OssResult? = null
        get() = if (field?.isExpired() == false) field else null

    val currentContainerResultsLS: MutableMap<SnykFile, List<ScanIssue>> = mutableMapOf()

    var currentContainerResult: ContainerResult? = null
        get() = if (field?.isExpired() == false) field else null

    val currentIacResultsLS: MutableMap<SnykFile, List<ScanIssue>> = mutableMapOf()

    var currentIacResult: IacResult? = null
        get() = if (field?.isExpired() == false) field else null

    var currentOssError: SnykError? = null

    var currentContainerError: SnykError? = null

    var currentIacError: SnykError? = null

    var currentSnykCodeError: SnykError? = null

    fun cleanCaches() {
        currentOSSResultsLS.clear()
        currentOssResults = null
        currentContainerResult = null
        currentIacResult = null
        currentOssError = null
        currentContainerError = null
        currentIacError = null
        currentSnykCodeResultsLS.clear()
        currentSnykCodeError = null
    }

    fun initCacheUpdater() {
        project.messageBus.connect().subscribe(
            SnykScanListener.SNYK_SCAN_TOPIC,
            object : SnykScanListener {

                override fun scanningStarted() {
                    currentOssError = null
                    currentSnykCodeError = null
                    currentIacError = null
                    currentContainerError = null
                }

                override fun scanningOssFinished(ossResult: OssResult) {
                    currentOssResults = ossResult
                }

                override fun scanningIacFinished(iacResult: IacResult) {
                    currentIacResult = iacResult
                }

                override fun scanningContainerFinished(containerResult: ContainerResult) {
                    currentContainerResult = containerResult
                }

                override fun scanningOssError(snykError: SnykError) {
                    currentOssResults = null
                    currentOssError =
                        when {
                            snykError.message.startsWith(SnykToolWindowPanel.NO_OSS_FILES) -> null
                            snykError.message.startsWith(SnykToolWindowPanel.AUTH_FAILED_TEXT) -> null
                            else -> snykError
                        }
                }

                override fun scanningIacError(snykError: SnykError) {
                    currentIacResult = null
                    currentIacError =
                        when {
                            snykError.message.startsWith(SnykToolWindowPanel.NO_IAC_FILES) -> null
                            snykError.message.startsWith(SnykToolWindowPanel.AUTH_FAILED_TEXT) -> null
                            else -> snykError
                        }
                }

                override fun scanningContainerError(snykError: SnykError) {
                    currentContainerResult = null
                    currentContainerError =
                        when {
                            snykError == ContainerService.NO_IMAGES_TO_SCAN_ERROR -> null
                            snykError.message.startsWith(SnykToolWindowPanel.AUTH_FAILED_TEXT) -> null
                            else -> snykError
                        }
                }
            }
        )

        project.messageBus.connect().subscribe(
            SnykScanListenerLS.SNYK_SCAN_TOPIC,
            object : SnykScanListenerLS {
                val logger = logger<SnykCachedResults>()
                override fun scanningStarted(snykScan: SnykScanParams) {
                    logger.info("scanningStarted for project ${project.name}")
                }

                override fun scanningSnykCodeFinished(snykResults: Map<SnykFile, List<ScanIssue>>) {
                    currentSnykCodeResultsLS.clear()
                    currentSnykCodeResultsLS.putAll(snykResults)
                    logger.info("scanning finished for project ${project.name}, assigning cache.")
                }

                override fun scanningOssFinished(snykResults: Map<SnykFile, List<ScanIssue>>) {
                    currentOSSResultsLS.clear()
                    currentOSSResultsLS.putAll(snykResults)
                    logger.info("scanning finished for project ${project.name}, assigning cache.")
                }

                override fun scanningError(snykScan: SnykScanParams) {
                    SnykBalloonNotificationHelper
                        .showError(
                            "scanning error for project ${project.name}, emptying cache.Data: $snykScan",
                            project
                        )
                }
            }
        )
    }
}

internal class SnykFileIssueComparator(
    private val snykResults: Map<SnykFile, List<ScanIssue>>
) : Comparator<SnykFile> {
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
