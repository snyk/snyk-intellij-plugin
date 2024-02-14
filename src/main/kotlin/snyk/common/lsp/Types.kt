@file:Suppress("unused")

package snyk.common.lsp

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getDocument
import io.snyk.plugin.toVirtualFile
import org.eclipse.lsp4j.Range

// Define the SnykScanParams data class
data class SnykScanParams(
    val status: String, // Status can be either Initial, InProgress or Success
    val product: String, // Product under scan (Snyk Code, Snyk Open Source, etc...)
    val folderPath: String, // FolderPath is the root-folder of the current scan
    val issues: List<ScanIssue> // Issues contain the scan results in the common issues model
)

// Define the ScanIssue data class
data class ScanIssue(
    val id: String, // Unique key identifying an issue in the whole result set. Not the same as the Snyk issue ID.
    val title: String,
    val severity: String,
    val filePath: String,
    val range: Range,
    val additionalData: IssueData
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
            } else field
        }

    private var document: Document?
        get() {
            return if (field == null) {
                field = virtualFile?.getDocument()
                field
            } else field
        }

    private var startOffset: Int?
        get() {
            return if (field == null) {
                field = document?.getLineStartOffset(range.start.line)?.plus(range.start.character)
                field
            } else field
        }

    private var endOffset: Int?
        get() {
            return if (field == null) {
                field = document?.getLineStartOffset(range.end.line)?.plus(range.end.character)
                field
            } else field
        }

    init {
        virtualFile = filePath.toVirtualFile()
        document = virtualFile?.getDocument()
        startOffset = document?.getLineStartOffset(range.start.line)?.plus(range.start.character)
        endOffset = document?.getLineStartOffset(range.end.line)?.plus(range.end.character)
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

    override fun compareTo(other: ScanIssue): Int {
        this.filePath.compareTo(other.filePath).let { if (it != 0) it else 0 }
        this.range.start.line.compareTo(other.range.start.line).let { if (it != 0) it else 0 }
        this.range.end.line.compareTo(other.range.end.line).let { if (it != 0) it else 0 }
        return 0
    }
}

data class ExampleCommitFix(
    @SerializedName("commitURL") val commitURL: String,
    @SerializedName("lines") val lines: List<CommitChangeLine>
)

data class CommitChangeLine(
    @SerializedName("line") val line: String,
    @SerializedName("lineNumber") val lineNumber: Int,
    @SerializedName("lineChange") val lineChange: String
)

typealias Point = Array<Int>?

data class Marker(
    @SerializedName("msg") val msg: Point,
    @SerializedName("pos") val pos: List<MarkerPosition>
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
    @SerializedName("file") val file: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MarkerPosition

        if (cols != null) {
            if (other.cols == null) return false
            if (!cols.contentEquals(other.cols)) return false
        } else if (other.cols != null) return false
        if (rows != null) {
            if (other.rows == null) return false
            if (!rows.contentEquals(other.rows)) return false
        } else if (other.rows != null) return false
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

data class IssueData(
    @SerializedName("message") val message: String,
    @SerializedName("leadURL") val leadURL: String?,
    @SerializedName("rule") val rule: String,
    @SerializedName("ruleId") val ruleId: String,
    @SerializedName("repoDatasetSize") val repoDatasetSize: Int,
    @SerializedName("exampleCommitFixes") val exampleCommitFixes: List<ExampleCommitFix>,
    @SerializedName("cwe") val cwe: List<String>?,
    @SerializedName("text") val text: String,
    @SerializedName("markers") val markers: List<Marker>?,
    @SerializedName("cols") val cols: Point,
    @SerializedName("rows") val rows: Point,
    @SerializedName("isSecurityType") val isSecurityType: Boolean,
    @SerializedName("priorityScore") val priorityScore: Int,
    @SerializedName("hasAIFix") val hasAIFix: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IssueData

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
        } else if (other.cols != null) return false
        if (rows != null) {
            if (other.rows == null) return false
            if (!rows.contentEquals(other.rows)) return false
        } else if (other.rows != null) return false
        if (isSecurityType != other.isSecurityType) return false
        if (priorityScore != other.priorityScore) return false
        if (hasAIFix != other.hasAIFix) return false

        return true
    }

    override fun hashCode(): Int {
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
        return result
    }
}

data class HasAuthenticatedParam (@SerializedName("token") val token: String?)

data class SnykTrustedFoldersParams(@SerializedName("trustedFolders") val trustedFolders: List<String>)
