package snyk.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.container.ContainerResult
import snyk.container.ContainerService
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service
class SnykCachedResults(val project: Project) {

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
        currentSnykCodeError = null
    }

    fun initCacheUpdater() = project.messageBus.connect().subscribe(
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

            override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?) {}

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
}
