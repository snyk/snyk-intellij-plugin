package snyk.container

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.services.CliAdapter
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError

private val LOG = logger<ContainerService>()

@Service
class ContainerService(project: Project) : CliAdapter<ContainerIssuesForImage, ContainerResult>(
    project = project
) {

    private var imageCache: KubernetesImageCache? = getKubernetesImageCache(project)

    @TestOnly
    fun setKubernetesImageCache(cache: KubernetesImageCache) {
        imageCache = cache
    }

    fun scan(): ContainerResult {
        LOG.debug("starting scanning container service")

        val patchedIssueImageList = mutableListOf<ContainerIssuesForImage>()

        val commands = listOf("container", "test")
        val imageNames = imageCache?.getKubernetesWorkloadImageNamesFromCache() ?: emptySet()
        LOG.debug("container scan requested for ${imageNames.size} images: $imageNames")
        if (imageNames.isEmpty()) {
            return ContainerResult(emptyList(), listOf(NO_IMAGES_TO_SCAN_ERROR))
        }
        val tempResult = execute(commands + imageNames)
        if (!tempResult.isSuccessful()) {
            LOG.debug("container service scan fail")
            return tempResult
        }
        LOG.debug(
            "container scan: images with vulns [${tempResult.allCliIssues?.size}], issues [${tempResult.issuesCount}] "
        )

        val images = imageCache?.getKubernetesWorkloadImages() ?: emptySet()
        tempResult.allCliIssues?.forEach { forImage ->
            val baseImageRemediationInfo = convertRemediation(forImage.docker.baseImageRemediation)
            val sanitizedImageName = sanitizeImageName(forImage.imageName, imageNames)
            val enrichedContainerIssuesForImage = forImage.copy(
                imageName = sanitizedImageName,
                workloadImages = images.filter { it.image == sanitizedImageName },
                baseImageRemediationInfo = baseImageRemediationInfo
            )
            patchedIssueImageList.add(enrichedContainerIssuesForImage)
        }
        LOG.debug("container service scan completed")
        return ContainerResult(patchedIssueImageList, tempResult.errors)
    }

    /**
     * Due to bug (feature?) in CLI, image names come transformed as below, so we need to sanitize them:
     * snyk/code-agent      -> snyk/code-agent/code-agent
     * jenkins/jenkins:lts  -> jenkins/jenkins:lts/jenkins
     * bitnami/kubectl:1.21 -> bitnami/kubectl:1.21/kubectl
     * ghcr.io/christophetd/log4shell-vulnerable-app -> ghcr.io/christophetd/log4shell-vulnerable-app/christophetd/log4shell-vulnerable-app
     */
    private fun sanitizeImageName(cliReturnedImageName: String, imageNamesToScan: Set<String>): String {
        val nameCandidates = imageNamesToScan.filter { cliReturnedImageName.startsWith(it) }
        return when {
            // trivial case: jenkins/jenkins:lts  -> jenkins/jenkins:lts/jenkins
            nameCandidates.size == 1 -> nameCandidates.first()
            // tricky case when we have:
            // jenkins/jenkins      -> jenkins/jenkins/jenkins
            // jenkins/jenkins:lts  -> jenkins/jenkins:lts/jenkins
            // so for `jenkins/jenkins:lts/jenkins` we should choose latter (longest match)
            nameCandidates.size > 1 -> nameCandidates.maxBy { it.length }!!
            // fallback in case our sanitizing fail
            else -> cliReturnedImageName
        }
    }

    fun convertRemediation(baseImageRemediation: BaseImageRemediation?): BaseImageRemediationInfo? {
        if (baseImageRemediation == null || !baseImageRemediation.isRemediationAvailable()) return null

        val adviceList = baseImageRemediation.advice
        val adviceListAsString = adviceList.joinToString(separator = "\n") { it.message }
        LOG.debug("\n" + adviceListAsString)
        // current image always first
        val currentImageRawString = adviceList[0].message
        val currentBaseImageInfo = BaseImageRemediationExtractor.extractImageInfo(currentImageRawString)
        var majorUpgradeInfo: BaseImageInfo? = null
        var minorUpgradeInfo: BaseImageInfo? = null
        var alternativeUpgradeInfo: BaseImageInfo? = null
        adviceList.forEachIndexed { index, advice ->
            if (advice.bold == true) {
                when (advice.message) {
                    "Major upgrades" -> {
                        majorUpgradeInfo = BaseImageRemediationExtractor.extractImageInfo(adviceList[index + 1].message)
                    }
                    "Minor upgrades" -> {
                        minorUpgradeInfo = BaseImageRemediationExtractor.extractImageInfo(adviceList[index + 1].message)
                    }
                    "Alternative image types" -> {
                        alternativeUpgradeInfo =
                            BaseImageRemediationExtractor.extractImageInfo(adviceList[index + 1].message)
                    }
                }
            }
        }

        return BaseImageRemediationInfo(
            currentImage = currentBaseImageInfo,
            majorUpgrades = majorUpgradeInfo,
            minorUpgrades = minorUpgradeInfo,
            alternativeUpgrades = alternativeUpgradeInfo
        )
    }

    override fun getProductResult(cliIssues: List<ContainerIssuesForImage>?, snykErrors: List<SnykError>): ContainerResult =
        ContainerResult(cliIssues, snykErrors)

    override fun sanitizeCliIssues(cliIssues: ContainerIssuesForImage): ContainerIssuesForImage =
        // .copy() will check nullability of fields
        cliIssues.copy(
            vulnerabilities = cliIssues.vulnerabilities.map { containerIssue ->
                containerIssue.copy(
                    identifiers = containerIssue.identifiers?.copy(),
                    // @Expose fields are `null` after Gson parser, so explicit init needed
                    obsolete = false,
                    ignored = false
                )
            },
            workloadImages = emptyList()
        )

    override fun getCliIIssuesClass(): Class<ContainerIssuesForImage> = ContainerIssuesForImage::class.java

    override fun buildExtraOptions(): List<String> = listOf("--json")

    companion object {
        val NO_IMAGES_TO_SCAN_ERROR = SnykError("", "")
    }
}
