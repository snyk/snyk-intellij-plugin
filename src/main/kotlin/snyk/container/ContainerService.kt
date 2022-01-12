package snyk.container

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.services.CliAdapter
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError

private val LOG = logger<ContainerService>()

@Service
class ContainerService(project: Project) : CliAdapter<ContainerResult>(
    project = project
) {

    private var imageCache: KubernetesImageCache? = getKubernetesImageCache(project)

    @TestOnly
    fun setKubernetesImageCache(cache: KubernetesImageCache) {
        imageCache = cache
    }

    fun scan(): ContainerResult {
        LOG.debug("starting scanning container service")

        val containerIssueImageList = mutableListOf<ContainerIssuesForImage>()

        val commands = listOf("container", "test")
        val images = imageCache?.getKubernetesWorkloadImages() ?: emptySet()
        val imageNames = images.map { it.image }
        LOG.debug("container scan requested for ${imageNames.size} images: $imageNames")
        if (imageNames.isEmpty()) {
            return ContainerResult(emptyList(), null)
        }
        val tempResult = execute(commands + imageNames)
        if (!tempResult.isSuccessful()) {
            LOG.debug("container service scan fail")
            return tempResult
        }
        LOG.debug(
            "container scan: images with vulns [${tempResult.allCliIssues?.size}], issues [${tempResult.issuesCount}] "
        )

        tempResult.allCliIssues?.forEach {
            val baseImageRemediationInfo = convertRemediation(it.docker.baseImageRemediation)

            val enrichedContainerIssuesForImage = it.copy(
                workloadImage = images.find { image -> image.image == it.imageName },
                baseImageRemediationInfo = baseImageRemediationInfo
            )
            containerIssueImageList.add(enrichedContainerIssuesForImage)
        }
        LOG.debug("container service scan completed")
        return ContainerResult(containerIssueImageList, null)
    }

    private fun convertRemediation(baseImageRemediation: BaseImageRemediation?): BaseImageRemediationInfo? {
        if (baseImageRemediation == null || !baseImageRemediation.isRemediationAvailable()) return null

        // current image always first
        val adviceList = baseImageRemediation.advice
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

    override fun getErrorResult(errorMsg: String): ContainerResult =
        ContainerResult(null, SnykError(errorMsg, projectPath))

    override fun convertRawCliStringToCliResult(rawStr: String): ContainerResult =
        try {
            val gson = Gson()
            when {
                rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> {
                    ContainerResult(null, null)
                }
                rawStr.isEmpty() -> {
                    ContainerResult(null, SnykError("CLI failed to produce any output", projectPath))
                }
                rawStr.first() == '[' -> {
                    // see https://sites.google.com/site/gson/gson-user-guide#TOC-Serializing-and-Deserializing-Collection-with-Objects-of-Arbitrary-Types
                    val jsonArray: JsonArray = JsonParser.parseString(rawStr).asJsonArray
                    ContainerResult(
                        jsonArray.mapNotNull {
                            val containerIssuesForImage = gson.fromJson(it, ContainerIssuesForImage::class.java)
                            // todo: save and show error for particular image test?
                            if (containerIssuesForImage.error != null) null else containerIssuesForImage
                        },
                        null)
                }
                rawStr.first() == '{' -> {
                    if (isSuccessCliJsonString(rawStr)) {
                        ContainerResult(listOf(gson.fromJson(rawStr, ContainerIssuesForImage::class.java)), null)
                    } else {
                        val cliError = gson.fromJson(rawStr, CliError::class.java)
                        ContainerResult(null, SnykError(cliError.message, cliError.path))
                    }
                }
                else -> {
                    ContainerResult(null, SnykError(rawStr, projectPath))
                }
            }
        } catch (e: JsonSyntaxException) {
            ContainerResult(null, SnykError(e.message ?: e.toString(), projectPath))
        }

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"vulnerabilities\":") && !jsonStr.contains("\"error\":")

    override fun buildExtraOptions(): List<String> = listOf("--json")
}
