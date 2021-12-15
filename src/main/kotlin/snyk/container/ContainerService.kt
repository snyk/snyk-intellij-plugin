package snyk.container

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.services.CliAdapter
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError
import java.lang.reflect.Type
import java.util.Collections

private val LOG = logger<ContainerService>()

@Service
class ContainerService(project: Project) : CliAdapter<ContainerResult>(
    project = project
) {

    private var imageCache: KubernetesImageCache = project.service()

    @TestOnly
    fun setKubernetesImageCache(cache: KubernetesImageCache) {
        imageCache = cache
    }

    fun scan(): ContainerResult {
        val images = imageCache.getKubernetesWorkloadImages()
        return this.scan(images)
    }

    private fun scan(images: Set<KubernetesWorkloadImage>): ContainerResult {
        LOG.debug("starting scanning container service")

        // we want this synchronized so we can parallel process the stream that fills it
        val containerIssueImageList = Collections.synchronizedList(mutableListOf<ContainerIssuesForImage>())

        var commands = buildCliCommandsList(listOf("container", "test", "--app-vulns"))
        val mutCommands = commands.toMutableList()
        images.parallelStream().forEach { image ->
            mutCommands.add(image.image)
            commands = mutCommands.toList()
            val result = execute(commands)

            result.allCliIssues?.forEach {
                val baseImageRemediationInfo = convertRemediation(it.docker.baseImageRemediation)

                val enrichedContainerIssuesForImage = it.copy(
                    workloadImage = image,
                    baseImageRemediationInfo = baseImageRemediationInfo
                )
                containerIssueImageList.add(enrichedContainerIssuesForImage)
            }
        }
        LOG.debug("container service scan completed")
        return ContainerResult(containerIssueImageList, null)
    }

    fun convertRemediation(baseImageRemediation: BaseImageRemediation?): BaseImageRemediationInfo? {
        if (baseImageRemediation == null || !baseImageRemediation.isRemediationAvailable()) return null

        // current image always first
        val adviceList = baseImageRemediation.advice
        val currentImageRawString = adviceList[0].message
        val currentBaseImageInfo = BaseImageRemediationExtractor.extractImageInfo(currentImageRawString)
        var majorUpgradeInfo: BaseImageInfo? = null
        var minorUpgradeInfo: BaseImageInfo? = null
        var alternativeUpgradeInfo: BaseImageInfo? = null
        adviceList.forEachIndexed { index, advice ->
            if (advice.bold != null && advice.bold) {
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
            when {
                rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> {
                    ContainerResult(null, null)
                }
                rawStr.isEmpty() -> {
                    ContainerResult(null, SnykError("CLI failed to produce any output", projectPath))
                }
                rawStr.first() == '[' -> {
                    ContainerResult(Gson().fromJson(rawStr, containerIssuesForFileListType()), null)
                }
                rawStr.first() == '{' -> {
                    if (isSuccessCliJsonString(rawStr)) {
                        ContainerResult(listOf(Gson().fromJson(rawStr, ContainerIssuesForImage::class.java)), null)
                    } else {
                        val cliError = Gson().fromJson(rawStr, CliError::class.java)
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

    private fun containerIssuesForFileListType(): Type =
        object : TypeToken<ArrayList<ContainerIssuesForImage>>() {}.type

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"vulnerabilities\":") && !jsonStr.contains("\"error\":")

    override fun buildExtraOptions(): List<String> {
        return emptyList()
    }
}
