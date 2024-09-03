@file:Suppress("unused", "SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL", "USELESS_ELVIS", "DuplicatedCode")

package snyk.common.lsp

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getDocument
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.PackageManagerIconProvider.Companion.getIcon
import org.eclipse.lsp4j.Range
import snyk.common.ProductType
import java.util.Date
import java.util.Locale
import javax.swing.Icon

// type CliOutput struct {
//	Code         int    `json:"code,omitempty"`
//	ErrorMessage string `json:"error,omitempty"`
//	Path         string `json:"path,omitempty"`
//	Command      string `json:"command,omitempty"`
//}
data class CliError (
    val code: Int? = 0,
    val error: String? = null,
    val path: String? = null,
    val command: String? = null,
)

// Define the SnykScanParams data class
data class SnykScanParams(
    val status: String, // Status can be either Initial, InProgress, Success or Error
    val product: String, // Product under scan (Snyk Code, Snyk Open Source, etc...)
    val folderPath: String, // FolderPath is the root-folder of the current scan
    val issues: List<ScanIssue>, // Issues contain the scan results in the common issues model
    val errorMessage: String? = null, // Error Message if applicable
    val cliError: CliError? = null, // structured error information if applicable
)

data class ErrorResponse(
    @SerializedName("error") val error: String,
    @SerializedName("path") val path: String
)

enum class LsProductConstants(val value: String) {
    OpenSource("Snyk Open Source"),
    Code("Snyk Code"),
    InfrastructureAsCode("Snyk IaC"),
    Container("Snyk Container"),
    Unknown("");

    override fun toString(): String {
        return value
    }
}


// Define the ScanIssue data class
data class ScanIssue(
    val id: String, // Unique key identifying an issue in the whole result set. Not the same as the Snyk issue ID.
    val title: String,
    val severity: String,
    val filePath: String,
    val range: Range,
    val additionalData: IssueData,
    var isIgnored: Boolean?,
    var ignoreDetails: IgnoreDetails?,
) : Comparable<ScanIssue> {
    var textRange: TextRange? = null
        get() {
            return if (startOffset == null || endOffset == null) {
                return null
            } else {
                field = TextRange(startOffset!!, endOffset!!)
                field
            }
        }
    var virtualFile: VirtualFile?
        get() {
            return if (field == null) {
                field = filePath.toVirtualFile()
                field
            } else {
                field
            }
        }

    private var document: Document?
        get() {
            return if (field == null) {
                field = virtualFile?.getDocument()
                field
            } else {
                field
            }
        }

    private var startOffset: Int?
        get() {
            return if (field == null) {
                field = document?.getLineStartOffset(range.start.line)?.plus(range.start.character)
                field
            } else {
                field
            }
        }

    private var endOffset: Int?
        get() {
            return if (field == null) {
                field = document?.getLineStartOffset(range.end.line)?.plus(range.end.character)
                field
            } else {
                field
            }
        }

    init {
        virtualFile = filePath.toVirtualFile()
        document = virtualFile?.getDocument()
        startOffset = document?.getLineStartOffset(range.start.line)?.plus(range.start.character)
        endOffset = document?.getLineStartOffset(range.end.line)?.plus(range.end.character)
    }

    fun title(): String {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> this.title
            ProductType.CODE_QUALITY -> {
                this.additionalData.message.split('.').firstOrNull() ?: "Unknown issue"
            }

            ProductType.CODE_SECURITY -> {
                this.title.split(":").firstOrNull() ?: "Unknown issue"
            }

            else -> TODO()
        }
    }

    fun longTitle(): String {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> {
                "${this.additionalData.packageName}@${this.additionalData.version}: ${this.title()}"
            }

            ProductType.CODE_QUALITY, ProductType.CODE_SECURITY -> {
                return "${this.title()} [${this.range.start.line + 1},${this.range.start.character}]"
            }

            else -> TODO()
        }
    }

    fun priority(): Int {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> {
                return when (this.getSeverityAsEnum()) {
                    Severity.CRITICAL -> 4
                    Severity.HIGH -> 3
                    Severity.MEDIUM -> 2
                    Severity.LOW -> 1
                    Severity.UNKNOWN -> 0
                }
            }

            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> this.additionalData.priorityScore
            else -> TODO()
        }
    }

    fun issueNaming(): String {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> {
                if (this.additionalData.license != null) {
                    "License"
                } else {
                    "Vulnerability"
                }
            }

            ProductType.CODE_SECURITY -> "Vulnerability"
            ProductType.CODE_QUALITY -> "Quality Issue"
            else -> TODO()
        }
    }

    fun cwes(): List<String> {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> {
                this.additionalData.identifiers?.CWE ?: emptyList()
            }

            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> {
                this.additionalData.cwe ?: emptyList()
            }

            else -> TODO()
        }
    }

    fun cves(): List<String> {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> {
                this.additionalData.identifiers?.CVE ?: emptyList()
            }

            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> emptyList()
            else -> TODO()
        }
    }

    fun cvssScore(): String? {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> this.additionalData.cvssScore
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> null
            else -> TODO()
        }
    }

    fun cvssV3(): String? {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> this.additionalData.CVSSv3
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> null
            else -> TODO()
        }
    }

    fun id(): String? {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> this.additionalData.ruleId
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> null
            else -> TODO()
        }
    }

    fun ruleId(): String {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS, ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> this.additionalData.ruleId
            else -> TODO()
        }
    }

    fun icon(): Icon? {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> getIcon(this.additionalData.packageManager.lowercase(Locale.getDefault()))
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> null
            else -> TODO()
        }
    }

    fun details(): String {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> this.additionalData.details ?: ""
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> this.additionalData.details ?: ""
            else -> TODO()
        }
    }

    fun annotationMessage(): String {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> this.title + " in " + this.additionalData.packageName + " id: " + this.ruleId()
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY ->
                this.title.ifBlank {
                    this.additionalData.message.let {
                        if (it.length < 70) it else "${it.take(70)}..."
                    } + " (Snyk)"
                }

            else -> TODO()
        }
    }

    fun canLoadSuggestionPanelFromHTML(): Boolean {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS -> true
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY ->
                pluginSettings().isGlobalIgnoresFeatureEnabled && this.additionalData.details != null

            else -> TODO()
        }
    }

    fun hasAIFix(): Boolean {
        return when (this.additionalData.getProductType()) {
            ProductType.OSS ->
                return this.additionalData.isUpgradable == true
            ProductType.CODE_SECURITY, ProductType.CODE_QUALITY -> {
                return this.additionalData.hasAIFix
            }

            else -> TODO()
        }
    }

    fun isIgnored(): Boolean {
        return pluginSettings().isGlobalIgnoresFeatureEnabled && this.isIgnored == true
    }

    fun getSeverityAsEnum(): Severity {
        return when (severity) {
            "critical" -> Severity.CRITICAL
            "high" -> Severity.HIGH
            "medium" -> Severity.MEDIUM
            "low" -> Severity.LOW
            else -> Severity.UNKNOWN
        }
    }

    fun isVisible(
        includeOpenedIssues: Boolean,
        includeIgnoredIssues: Boolean,
    ): Boolean {
        if (includeIgnoredIssues && includeOpenedIssues) {
            return true
        }
        if (includeIgnoredIssues) {
            return this.isIgnored == true
        }
        if (includeOpenedIssues) {
            return this.isIgnored != true
        }
        return false
    }

    override fun compareTo(other: ScanIssue): Int {
        this.filePath.compareTo(other.filePath).let { if (it != 0) it else 0 }
        this.range.start.line.compareTo(other.range.start.line).let { if (it != 0) it else 0 }
        this.range.end.line.compareTo(other.range.end.line).let { if (it != 0) it else 0 }
        return 0
    }
}

data class ExampleCommitFix(
    @SerializedName("commitURL") val commitURL: String,
    @SerializedName("lines") val lines: List<CommitChangeLine>,
)

data class CommitChangeLine(
    @SerializedName("line") val line: String,
    @SerializedName("lineNumber") val lineNumber: Int,
    @SerializedName("lineChange") val lineChange: String,
)

typealias Point = Array<Int>?

data class Marker(
    @SerializedName("msg") val msg: Point,
    @SerializedName("pos") val pos: List<MarkerPosition>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Marker

        if (!msg.contentEquals(other.msg)) return false
        if (pos != other.pos) return false

        return true
    }

    override fun hashCode(): Int {
        var result = msg.contentHashCode()
        result = 31 * result + pos.hashCode()
        return result
    }
}

data class MarkerPosition(
    @SerializedName("cols") val cols: Point,
    @SerializedName("rows") val rows: Point,
    @SerializedName("file") val file: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MarkerPosition

        if (cols != null) {
            if (other.cols == null) return false
            if (!cols.contentEquals(other.cols)) return false
        } else if (other.cols != null) {
            return false
        }
        if (rows != null) {
            if (other.rows == null) return false
            if (!rows.contentEquals(other.rows)) return false
        } else if (other.rows != null) {
            return false
        }
        if (file != other.file) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cols?.contentHashCode() ?: 0
        result = 31 * result + (rows?.contentHashCode() ?: 0)
        result = 31 * result + file.hashCode()
        return result
    }
}

data class DataFlow(
    @SerializedName("position") val position: Int,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("flowRange") val flowRange: Range,
    @SerializedName("content") val content: String,
)

data class IssueData(
    // Code
    @SerializedName("message") val message: String,
    @SerializedName("leadURL") val leadURL: String?,
    @SerializedName("rule") val rule: String,
    @SerializedName("repoDatasetSize") val repoDatasetSize: Int,
    @SerializedName("exampleCommitFixes") val exampleCommitFixes: List<ExampleCommitFix>,
    @SerializedName("cwe") val cwe: List<String>?,
    @SerializedName("text") val text: String,
    @SerializedName("markers") val markers: List<Marker>?,
    @SerializedName("cols") val cols: Point,
    @SerializedName("rows") val rows: Point,
    @SerializedName("isSecurityType") val isSecurityType: Boolean,
    @SerializedName("priorityScore") val priorityScore: Int,
    @SerializedName("hasAIFix") val hasAIFix: Boolean,
    @SerializedName("dataFlow") val dataFlow: List<DataFlow>,
    // OSS
    @SerializedName("license") val license: String?,
    @SerializedName("identifiers") val identifiers: OssIdentifiers?,
    @SerializedName("description") val description: String,
    @SerializedName("language") val language: String,
    @SerializedName("packageManager") val packageManager: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("name") val name: String,
    @SerializedName("version") val version: String,
    @SerializedName("exploit") val exploit: String?,
    @SerializedName("CVSSv3") val CVSSv3: String?,
    @SerializedName("cvssScore") val cvssScore: String?,
    @SerializedName("fixedIn") val fixedIn: List<String>?,
    @SerializedName("from") val from: List<String>,
    @SerializedName("upgradePath") val upgradePath: List<Any>,
    @SerializedName("isPatchable") val isPatchable: Boolean,
    @SerializedName("isUpgradable") val isUpgradable: Boolean,
    @SerializedName("projectName") val projectName: String,
    @SerializedName("displayTargetFile") val displayTargetFile: String?,
    @SerializedName("matchingIssues") val matchingIssues: List<IssueData>,
    @SerializedName("lesson") val lesson: String?,
    // Code and OSS
    @SerializedName("ruleId") val ruleId: String,
    @SerializedName("details") val details: String?,
) {
    fun getProductType(): ProductType {
        // TODO: how else to differentiate?
        if (this.packageManager != null) {
            return ProductType.OSS
        }
        if (this.message != null) {
            return if (this.isSecurityType) {
                ProductType.CODE_SECURITY
            } else {
                ProductType.CODE_QUALITY
            }
        }
        throw IllegalStateException("Not defined type of IssueData")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IssueData

        if (this.getProductType() == ProductType.OSS) {
            if (ruleId != other.ruleId) return false
            if (license != other.license) return false
            if (identifiers != other.identifiers) return false
            if (description != other.description) return false
            if (language != other.language) return false
            if (packageManager != other.packageManager) return false
            if (packageName != other.packageName) return false
            if (name != other.name) return false
            if (version != other.version) return false
            if (exploit != other.exploit) return false
            if (CVSSv3 != other.CVSSv3) return false
            if (cvssScore != other.cvssScore) return false
            if (fixedIn != other.fixedIn) return false
            if (from != other.from) return false
            if (upgradePath != other.upgradePath) return false
            if (isPatchable != other.isPatchable) return false
            if (isUpgradable != other.isUpgradable) return false
            if (projectName != other.projectName) return false
            if (displayTargetFile != other.displayTargetFile) return false
            if (matchingIssues != other.matchingIssues) return false
            if (lesson != other.lesson) return false
            if (details != other.details) return false

            return true
        }

        if (message != other.message) return false
        if (leadURL != other.leadURL) return false
        if (rule != other.rule) return false
        if (ruleId != other.ruleId) return false
        if (repoDatasetSize != other.repoDatasetSize) return false
        if (exampleCommitFixes != other.exampleCommitFixes) return false
        if (cwe != other.cwe) return false
        if (text != other.text) return false
        if (markers != other.markers) return false
        if (cols != null) {
            if (other.cols == null) return false
            if (!cols.contentEquals(other.cols)) return false
        } else if (other.cols != null) {
            return false
        }
        if (rows != null) {
            if (other.rows == null) return false
            if (!rows.contentEquals(other.rows)) return false
        } else if (other.rows != null) {
            return false
        }
        if (isSecurityType != other.isSecurityType) return false
        if (priorityScore != other.priorityScore) return false
        if (hasAIFix != other.hasAIFix) return false
        if (dataFlow != other.dataFlow) return false
        if (details != other.details) return false

        return true
    }

    override fun hashCode(): Int {
        if (this.getProductType() == ProductType.OSS) {
            var result = ruleId.hashCode()
            result = 31 * result + description.hashCode()
            result = 31 * result + (license?.hashCode() ?: 0)
            result = 31 * result + (identifiers?.hashCode() ?: 0)
            result = 31 * result + language.hashCode()
            result = 31 * result + packageManager.hashCode()
            result = 31 * result + packageName.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + version.hashCode()
            result = 31 * result + (exploit?.hashCode() ?: 0)
            result = 31 * result + (CVSSv3?.hashCode() ?: 0)
            result = 31 * result + (cvssScore?.hashCode() ?: 0)
            result = 31 * result + (fixedIn?.hashCode() ?: 0)
            result = 31 * result + from.hashCode()
            result = 31 * result + upgradePath.hashCode()
            result = 31 * result + isPatchable.hashCode()
            result = 31 * result + (isUpgradable?.hashCode() ?: 0)
            result = 31 * result + displayTargetFile.hashCode()
            result = 31 * result + (details?.hashCode() ?: 0)
            result = 31 * result + (matchingIssues?.hashCode() ?: 0)
            result = 31 * result + (lesson?.hashCode() ?: 0)
            return result
        }

        var result = message.hashCode()
        result = 31 * result + (leadURL?.hashCode() ?: 0)
        result = 31 * result + rule.hashCode()
        result = 31 * result + ruleId.hashCode()
        result = 31 * result + repoDatasetSize
        result = 31 * result + exampleCommitFixes.hashCode()
        result = 31 * result + (cwe?.hashCode() ?: 0)
        result = 31 * result + text.hashCode()
        result = 31 * result + (markers?.hashCode() ?: 0)
        result = 31 * result + (cols?.contentHashCode() ?: 0)
        result = 31 * result + (rows?.contentHashCode() ?: 0)
        result = 31 * result + isSecurityType.hashCode()
        result = 31 * result + priorityScore
        result = 31 * result + hasAIFix.hashCode()
        result = 31 * result + dataFlow.hashCode()
        result = 31 * result + details.hashCode()
        return result
    }
}

data class HasAuthenticatedParam(
    @SerializedName("token") val token: String?,
)

data class SnykTrustedFoldersParams(
    @SerializedName("trustedFolders") val trustedFolders: List<String>,
)

data class IgnoreDetails(
    @SerializedName("category") val category: String,
    @SerializedName("reason") val reason: String,
    @SerializedName("expiration") val expiration: String,
    @SerializedName("ignoredOn") val ignoredOn: Date,
    @SerializedName("ignoredBy") val ignoredBy: String,
)

data class OssIdentifiers(
    @SerializedName("CWE") val CWE: List<String>?,
    @SerializedName("CVE") val CVE: List<String>?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OssIdentifiers

        if (CWE != other.CWE) return false
        if (CVE != other.CVE) return false

        return true
    }

    override fun hashCode(): Int {
        var result = CWE.hashCode()
        result = 31 * result + (CVE?.hashCode() ?: 0)
        return result
    }
}

data class FolderConfigsParam(
    @SerializedName("folderConfigs") val folderConfigs: List<FolderConfig>?,
)

data class FolderConfig(
    @SerializedName("folderPath") val folderPath: String,
    @SerializedName("baseBranch") val baseBranch: String,
    @SerializedName("localBranches") val localBranches: List<String> = emptyList()
)
