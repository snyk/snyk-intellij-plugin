@file:Suppress("unused", "DuplicatedCode")

package snyk.common.lsp

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getDocument
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.PackageManagerIconProvider.Companion.getIcon
import org.eclipse.lsp4j.Range
import java.util.Date
import java.util.Locale
import javax.swing.Icon


typealias FilterableIssueType = String

data class LsSdk(val type: String, val path: String)

data class CliError(
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
    var isNew: Boolean?,
    var filterableIssueType: FilterableIssueType,
    var ignoreDetails: IgnoreDetails?,
) : Comparable<ScanIssue> {

    companion object {
        const val OPEN_SOURCE: FilterableIssueType = "Open Source"
        const val CODE_SECURITY: FilterableIssueType = "Code Security"
        const val CODE_QUALITY: FilterableIssueType = "Code Quality"
        const val INFRASTRUCTURE_AS_CODE: FilterableIssueType = "Infrastructure As Code"
        const val CONTAINER: FilterableIssueType = "Container"
    }

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
        return when (this.filterableIssueType) {
            OPEN_SOURCE, INFRASTRUCTURE_AS_CODE -> this.title
            CODE_QUALITY -> {
                this.additionalData.message.split('.').firstOrNull() ?: "Unknown issue"
            }

            CODE_SECURITY -> {
                this.title.split(":").firstOrNull() ?: "Unknown issue"
            }

            else -> TODO()
        }
    }

    fun longTitle(): String {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> {
                "${this.additionalData.packageName}@${this.additionalData.version}: ${this.title()}"
            }

            CODE_QUALITY, CODE_SECURITY, INFRASTRUCTURE_AS_CODE -> {
                return "${this.title()} [${this.range.start.line + 1},${this.range.start.character}]"
            }

            else -> TODO()
        }
    }

    fun priority(): Int {
        return when (this.filterableIssueType) {
            OPEN_SOURCE, INFRASTRUCTURE_AS_CODE -> {
                return when (this.getSeverityAsEnum()) {
                    Severity.CRITICAL -> 4
                    Severity.HIGH -> 3
                    Severity.MEDIUM -> 2
                    Severity.LOW -> 1
                    Severity.UNKNOWN -> 0
                }
            }

            CODE_QUALITY, CODE_SECURITY -> this.additionalData.priorityScore
            else -> TODO()
        }
    }

    fun issueNaming(): String {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> {
                if (this.additionalData.license != null) {
                    "License"
                } else {
                    "Issue"
                }
            }

            CODE_SECURITY -> "Security Issue"
            CODE_QUALITY -> "Quality Issue"
            INFRASTRUCTURE_AS_CODE -> "Configuration Issue"
            else -> TODO()
        }
    }

    fun cwes(): List<String> {
        return when (this.filterableIssueType) {
            OPEN_SOURCE, INFRASTRUCTURE_AS_CODE -> {
                this.additionalData.identifiers?.CWE ?: emptyList()
            }

            CODE_QUALITY, CODE_SECURITY -> {
                this.additionalData.cwe ?: emptyList()
            }

            else -> TODO()
        }
    }

    fun cves(): List<String> {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> {
                this.additionalData.identifiers?.CVE ?: emptyList()
            }

            CODE_QUALITY, CODE_SECURITY, INFRASTRUCTURE_AS_CODE -> emptyList()
            else -> TODO()
        }
    }

    fun cvssScore(): String? {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> this.additionalData.cvssScore
            else -> null
        }
    }

    fun cvssV3(): String? {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> this.additionalData.CVSSv3
            else -> null
        }
    }

    fun id(): String? {
        return when (this.filterableIssueType) {
            OPEN_SOURCE, INFRASTRUCTURE_AS_CODE -> this.additionalData.ruleId
            else -> null
        }
    }

    fun ruleId(): String {
        return this.additionalData.ruleId
    }

    fun icon(): Icon? {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> getIcon(this.additionalData.packageManager.lowercase(Locale.getDefault()))
            else -> null
        }
    }

    fun details(): String {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> this.additionalData.details ?: ""
            else -> this.additionalData.details ?: ""
        }
    }

    fun annotationMessage(): String {
        return when (this.filterableIssueType) {
            OPEN_SOURCE -> this.title + " in " + this.additionalData.packageName + " id: " + this.ruleId()
            else ->
                this.title.ifBlank {
                    this.additionalData.message.let {
                        if (it.length < 70) it else "${it.take(70)}..."
                    } + " (Snyk)"
                }

        }
    }

    fun canLoadSuggestionPanelFromHTML(): Boolean = true

    fun hasAIFix(): Boolean {
        return this.additionalData.isUpgradable || this.additionalData.hasAIFix
    }

    fun isIgnored(): Boolean = this.isIgnored == true

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

data class Fix(
    @SerializedName("fixId") val fixId: String,
    @SerializedName("unifiedDiffsPerFile") val unifiedDiffsPerFile: Map<String, String>
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

@Suppress("PropertyName")
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
    // IAC
    @SerializedName("publicId") val publicId: String,
    @SerializedName("documentation") val documentation: String,
    @SerializedName("lineNumber") val lineNumber: String,
    @SerializedName("issue") val issue: String,
    @SerializedName("impact") val impact: String,
    @SerializedName("resolve") val resolve: String,
    @SerializedName("path") val path: List<String>,
    @SerializedName("references") val references: List<String>,
    @SerializedName("customUIContent") val customUIContent: String,

    // all
    @SerializedName("key") val key: String,
    @SerializedName("ruleId") val ruleId: String,
    @SerializedName("details") val details: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IssueData

        return this.key == other.key
    }

    override fun hashCode(): Int {
        return this.key.hashCode()
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

@Suppress("PropertyName")
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
    @SerializedName("localBranches") val localBranches: List<String> = emptyList(),
    @SerializedName("additionalParameters") val additionalParameters: List<String> = emptyList()
)
