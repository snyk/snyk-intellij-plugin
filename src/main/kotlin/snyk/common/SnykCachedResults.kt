package snyk.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykCodeScanListenerLS
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.ScanState
import snyk.common.lsp.SnykScanParams
import snyk.container.ContainerResult
import snyk.container.ContainerService
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service
class SnykCachedResults(val project: Project) {

    val currentSnykCodeResultsLS: MutableMap<SnykCodeFile, List<ScanIssue>> = mutableMapOf()

    var currentSnykCodeResults: SnykCodeResults? = null

    var currentOssResults: OssResult? = null
        get() = if (field?.isExpired() == false) field else null

    var currentContainerResult: ContainerResult? = null
        get() = if (field?.isExpired() == false) field else null

    var currentIacResult: IacResult? = null
        get() = if (field?.isExpired() == false) field else null

    var currentOssError: SnykError? = null

    var currentContainerError: SnykError? = null

    var currentIacError: SnykError? = null

    var currentSnykCodeError: SnykError? = null

    fun cleanCaches() {
        currentOssResults = null
        currentContainerResult = null
        currentIacResult = null
        currentOssError = null
        currentContainerError = null
        currentIacError = null
//        currentSnykCodeResultsLS = null
        currentSnykCodeResults = null
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
                    currentSnykCodeResults = null
                }

                override fun scanningOssFinished(ossResult: OssResult) {
                    currentOssResults = ossResult
                }

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?) {
                    currentSnykCodeResults = snykCodeResults
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

                override fun scanningSnykCodeError(snykError: SnykError) {
                    AnalysisData.instance.resetCachesAndTasks(project)
                    currentSnykCodeError = snykError
                }
            }
        )

        project.messageBus.connect().subscribe(
            SnykCodeScanListenerLS.SNYK_SCAN_TOPIC,
            object : SnykCodeScanListenerLS {
                val logger = logger<SnykCachedResults>()
                override fun scanningStarted(snykScan: SnykScanParams) {
                    if (snykScan.product != ScanState.SNYK_CODE) return
                    logger.info("scanningStarted for project ${project.name}, emptying cache.")
                }

                override fun scanningSnykCodeFinished(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>) {
                    currentSnykCodeResultsLS.putAll(snykCodeResults)
                    logger.info("scanning finished for project ${project.name}, assigning cache.")
                }

                override fun scanningSnykCodeError(snykScan: SnykScanParams) {
                    if (snykScan.product != ScanState.SNYK_CODE) return

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

internal class SnykCodeFileIssueComparator(
    private val snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>
) : Comparator<SnykCodeFile> {
    override fun compare(o1: SnykCodeFile, o2: SnykCodeFile): Int {
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

    private fun getCount(file: SnykCodeFile, severity: Severity) =
        snykCodeResults[file]?.filter { it.getSeverityAsEnum() == severity }?.size ?: 0
}
