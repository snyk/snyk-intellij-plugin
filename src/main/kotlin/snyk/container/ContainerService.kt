package snyk.container

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliService
import snyk.common.SnykError
import snyk.iac.IacResult
import java.nio.file.Paths

private val LOG = logger<ContainerService>()

@Service
class ContainerService(project: Project) : CliService<ContainerResult>(
    project = project,
    cliCommands = listOf("container", "test")
) {

    fun scan(iacResult: IacResult): ContainerResult {
        LOG.info("starting scanning container service")

        val issuesForFile = mutableListOf<ContainerIssuesForFile>()

        iacResult.allCliIssues?.forEach { iacIssuesForFile ->
            // at the moment we check only kubernetes manifest files
            if (iacIssuesForFile.targetFile.endsWith("yaml") ||
                iacIssuesForFile.targetFile.endsWith("yml")
            ) {
                val imageLineNumbers = ReadAction.compute<List<Pair<Int?, String>>, RuntimeException> {
                    findImageLineNumberOrNull(iacIssuesForFile.targetFile)
                }

                if (imageLineNumbers.isNotEmpty()) {
                    imageLineNumbers.forEach { lineWithImage ->
                        LOG.warn("start scanning: ${lineWithImage.second}")

                        if (lineWithImage.first != null) {
                            // extract image name
                            val imageName = lineWithImage.second.trim().replace("image:", "").trim()
                            LOG.info("Found image tag for container scan: $imageName")

                            var commands = buildCliCommandsList()
                            val mutCommands = commands.toMutableList()
                            mutCommands.add(imageName)
                            commands = mutCommands.toList()

                            val apiToken = pluginSettings().token ?: ""
                            val rawResultStr = ConsoleCommandRunner().execute(commands, projectPath, apiToken, project)
                            val result = convertRawCliStringToCliResult(rawResultStr)
                            result.allCliIssues?.forEach {
                                it.targetFile = iacIssuesForFile.targetFile
                                it.imageName = imageName
                                it.lineNumber = lineWithImage.first!!

                                val baseImageRemediationInfo = ReadAction.compute<BaseImageRemediationInfo, RuntimeException> {
                                    convertBaseImageRemediationInfo(it.docker.baseImageRemediation)
                                }
                                it.baseImageRemediationInfo = baseImageRemediationInfo

                                issuesForFile.add(it)
                            }
                        }
                    }
                }
            }
        }

        LOG.warn("container service scan completed")

        return ContainerResult(issuesForFile.toTypedArray(), null)
    }

    private fun findImageLineNumberOrNull(fileName: String): List<Pair<Int?, String>> {
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(
            Paths.get(project.basePath!!, fileName)
        ) ?: return listOf(Pair(null, ""))

        if (!virtualFile.isValid) {
            return listOf(Pair(null, ""))
        }

        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (document != null) {
            val imageLineNumbers = mutableListOf<Pair<Int?, String>>()
            for (line in 0 until document.lineCount) {
                val start = document.getLineStartOffset(line)
                val end = document.getLineEndOffset(line)

                val text = document.getText(TextRange(start, end))
                if (text.contains("image")) {
                    imageLineNumbers.add(Pair(line, text))
                }
            }
            return imageLineNumbers
        }

        return listOf(Pair(null, ""))
    }

    private fun convertBaseImageRemediationInfo(baseImageRemediation: BaseImageRemediation?): BaseImageRemediationInfo? {
        if (baseImageRemediation == null || !baseImageRemediation.isRemediationAvailable()) return null

        // current image always first
        val currentImageRawString = baseImageRemediation.advice[0].message
        val currentBaseImageInfo = BaseImageRemediationExtractor.extractImageInfo(currentImageRawString)
        var majorUpgradeInfo: BaseImageInfo? = null
        var minorUpgradeInfo: BaseImageInfo? = null
        var alternativeUpgradeInfo: BaseImageInfo? = null

        var minorUpgrade = false
        var majorUpgrade = false
        var alternativeUpgrade = false

        val advices = baseImageRemediation.advice.drop(1)
        for (advice in advices) {
            if (advice.bold != null && advice.bold!! &&
                advice.message == "Major upgrades"
            ) {
                majorUpgrade = true
                minorUpgrade = false
                alternativeUpgrade = false
                continue
            }

            if (advice.bold != null && advice.bold!! &&
                advice.message == "Minor upgrades"
            ) {
                majorUpgrade = false
                minorUpgrade = true
                alternativeUpgrade = false
                continue
            }

            if (advice.bold != null && advice.bold!! &&
                advice.message == "Alternative image types"
            ) {
                majorUpgrade = false
                minorUpgrade = false
                alternativeUpgrade = true
                continue
            }

            if (majorUpgrade) {
                majorUpgrade = false
                majorUpgradeInfo = BaseImageRemediationExtractor.extractImageInfo(advice.message)
            }

            if (minorUpgrade) {
                minorUpgrade = false
                minorUpgradeInfo = BaseImageRemediationExtractor.extractImageInfo(advice.message)
            }

            if (alternativeUpgrade) {
                alternativeUpgrade = false
                alternativeUpgradeInfo = BaseImageRemediationExtractor.extractImageInfo(advice.message)
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
                    ContainerResult(Gson().fromJson(rawStr, Array<ContainerIssuesForFile>::class.java), null)
                }
                rawStr.first() == '{' -> {
                    if (isSuccessCliJsonString(rawStr)) {
                        ContainerResult(arrayOf(Gson().fromJson(rawStr, ContainerIssuesForFile::class.java)), null)
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

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"vulnerabilities\":") && !jsonStr.contains("\"error\":")
}
