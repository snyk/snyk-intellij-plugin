package snyk.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.snykcode.SnykCodeResults
import snyk.container.ContainerResult
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

    fun initCacheUpdater() = project.messageBus.connect().subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        object : SnykScanListener {

            override fun scanningStarted() {}

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
            }

            override fun scanningIacError(snykError: SnykError) {
                currentIacResult = null
            }

            override fun scanningContainerError(snykError: SnykError) {
                currentContainerResult = null
            }

            override fun scanningSnykCodeError(snykError: SnykError) {}
        }
    )
}
