@file:Suppress("unused", "DuplicatedCode")

package snyk.common.lsp

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getDocument
import io.snyk.plugin.getSafeOffset
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.PackageManagerIconProvider.Companion.getIcon
import java.util.Date
import java.util.Locale
import javax.swing.Icon
import org.eclipse.lsp4j.Range
import snyk.common.ProductType

typealias FilterableIssueType = String

data class LsSdk(val type: String, val path: String)

data class CliError(
  val code: Int? = 0,
  val error: String? = null,
  val path: String? = null,
  val command: String? = null,
)

data class PresentableError(
  val code: Int? = null, // Error code
  val error: String? = null, // Error message
  val path: String? = null, // Path where the error occurred
  val command: String? = null, // Command that caused the error
  val showNotification: Boolean =
    false, // ShowNotification is for IDE to decide to display a notification or not
  val treeNodeSuffix: String =
    "", // TreeNodeSuffix is an optional suffix message to be displayed in the tree node in IDEs
) {
  fun toSnykError(defaultPath: String = ""): snyk.common.SnykError =
    snyk.common.SnykError(
      message = error ?: treeNodeSuffix,
      path = path ?: defaultPath,
      code = code,
    )
}

// Define the SnykScanParams data class
data class SnykScanParams(
  val status: String, // Status (Must map to an LsScanStatus enum)
  val product: String, // Product under scan (Must map to an LsProduct)
  val folderPath: String, // FolderPath is the root-folder of the current scan
  val presentableError: PresentableError? =
    null, // PresentableError structured error object for displaying it to the user
)

data class SnykScanSummaryParams(
  val scanSummary: String // HTML representation of the scan summary
)

data class SnykTreeViewParams(val treeViewHtml: String = "", val totalIssues: Int = 0)

data class AiFixParams(val issueId: String, val product: ProductType)

data class ErrorResponse(
  @SerializedName("error") val error: String,
  @SerializedName("path") val path: String,
)

enum class LsProduct(val longName: String, val shortName: String) {
  OpenSource("Snyk Open Source", "oss"),
  Code("Snyk Code", "code"),
  InfrastructureAsCode("Snyk IaC", "iac"),
  Secrets("Snyk Secrets", "secrets"),
  Unknown("", "");

  companion object {
    fun getFor(name: String): LsProduct =
      entries.toTypedArray().firstOrNull { name in arrayOf(it.longName, it.shortName) } ?: Unknown
  }
}

enum class LsScanState(val value: String) {
  Initial("initial"),
  InProgress("inProgress"),
  Success("success"),
  Error("error");

  override fun toString(): String = value
}

// Define the ScanIssue data class
data class ScanIssue(
  val id:
    String, // Unique key identifying an issue in the whole result set. Not the same as the Snyk
  // issue ID.
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
  var project: Project? = null

  companion object {
    const val OPEN_SOURCE: FilterableIssueType = "Open Source"
    const val CODE_SECURITY: FilterableIssueType = "Code Security"
    const val INFRASTRUCTURE_AS_CODE: FilterableIssueType = "Infrastructure As Code"
    const val SECRETS: FilterableIssueType = "Secrets"
  }

  var textRange: TextRange? = null
    get() {
      return if (startOffset == null || endOffset == null) {
        null
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
        field = document?.getSafeOffset(range.start.line, range.start.character)
        field
      } else {
        field
      }
    }

  private var endOffset: Int?
    get() {
      return if (field == null) {
        field = document?.getSafeOffset(range.end.line, range.end.character)
        field
      } else {
        field
      }
    }

  init {
    virtualFile = filePath.toVirtualFile()
    document = virtualFile?.getDocument()
    startOffset = document?.getSafeOffset(range.start.line, range.start.character)
    endOffset = document?.getSafeOffset(range.end.line, range.end.character)
  }

  fun title(): String =
    when (this.filterableIssueType) {
      OPEN_SOURCE,
      INFRASTRUCTURE_AS_CODE -> this.title
      CODE_SECURITY,
      SECRETS -> {
        this.title.split(":").firstOrNull() ?: "Unknown issue"
      }
      else -> this.title
    }

  fun longTitle(): String {
    val rangeBracket = "[${this.range.start.line + 1},${this.range.start.character}]"
    val packageInfo =
      if (!this.additionalData.packageName.isNullOrEmpty()) {
        "${this.additionalData.packageName}@${this.additionalData.version}: "
      } else {
        ""
      }
    return packageInfo + this.title() + ": " + rangeBracket
  }

  fun priority(): Int {
    // Severity is the primary sort key (multiplied by 1_000_000 to guarantee it dominates)
    val severityPriority =
      when (this.getSeverityAsEnum()) {
        Severity.CRITICAL -> 4_000_000
        Severity.HIGH -> 3_000_000
        Severity.MEDIUM -> 2_000_000
        Severity.LOW -> 1_000_000
        Severity.UNKNOWN -> 0
      }

    return if (this.additionalData.riskScore > 0) {
      severityPriority + this.additionalData.riskScore
    } else {
      severityPriority + this.additionalData.priorityScore
    }
  }

  fun issueNaming(): String =
    when (this.filterableIssueType) {
      OPEN_SOURCE -> {
        if (this.additionalData.license != null) {
          "License"
        } else {
          "Issue"
        }
      }
      CODE_SECURITY -> "Security Issue"
      INFRASTRUCTURE_AS_CODE -> "Infrastructure as Code Issue"
      SECRETS -> "Secrets Issue"
      else -> TODO()
    }

  fun cwes(): List<String> =
    (this.additionalData.identifiers?.CWE.orEmpty()) + (this.additionalData.cwe.orEmpty())

  fun cves(): List<String> = this.additionalData.identifiers?.CVE.orEmpty()

  fun cvssScore(): String? = this.additionalData.cvssScore

  fun cvssV3(): String? = this.additionalData.CVSSv3

  fun id(): String? =
    when (this.filterableIssueType) {
      OPEN_SOURCE,
      INFRASTRUCTURE_AS_CODE -> this.additionalData.ruleId
      else -> null
    }

  fun ruleId(): String = this.additionalData.ruleId

  fun icon(): Icon? =
    when (this.filterableIssueType) {
      OPEN_SOURCE -> getIcon(this.additionalData.packageManager.lowercase(Locale.getDefault()))
      else -> null
    }

  fun details(project: Project): String =
    when (this.filterableIssueType) {
      OPEN_SOURCE,
      CODE_SECURITY,
      SECRETS -> getHtml(this.additionalData.details, project)
      INFRASTRUCTURE_AS_CODE -> getHtml(this.additionalData.customUIContent, project)
      else -> ""
    }

  private fun getHtml(details: String?, project: Project): String =
    if (details.isNullOrEmpty() && this.id.isNotBlank()) {
      LanguageServerWrapper.getInstance(project).generateIssueDescription(this) ?: ""
    } else {
      details ?: ""
    }

  override fun hashCode(): Int = this.id.hashCode()

  fun annotationMessage(): String =
    when (this.filterableIssueType) {
      OPEN_SOURCE -> this.title + " in " + this.additionalData.packageName + " id: " + this.ruleId()
      else ->
        this.title.ifBlank {
          this.additionalData.message.let { if (it.length < 70) it else "${it.take(70)}..." } +
            " (Snyk)"
        }
    }

  fun hasAIFix(): Boolean = this.additionalData.isUpgradable || this.additionalData.hasAIFix

  fun isIgnored(): Boolean = this.isIgnored == true

  fun getSeverityAsEnum(): Severity =
    when (severity) {
      "critical" -> Severity.CRITICAL
      "high" -> Severity.HIGH
      "medium" -> Severity.MEDIUM
      "low" -> Severity.LOW
      else -> Severity.UNKNOWN
    }

  fun isVisible(includeOpenedIssues: Boolean, includeIgnoredIssues: Boolean): Boolean {
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ScanIssue

    return id == other.id
  }
}

data class ExampleCommitFix(
  @SerializedName("commitURL") val commitURL: String,
  @SerializedName("lines") val lines: List<CommitChangeLine>,
)

data class Fix(
  @SerializedName("fixId") val fixId: String,
  @SerializedName("unifiedDiffsPerFile") val unifiedDiffsPerFile: Map<String, String>,
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
  @SerializedName("customUIContent") val customUIContent: String?,

  // all
  @SerializedName("key") val key: String,
  @SerializedName("ruleId") val ruleId: String,
  @SerializedName("details") val details: String?,
  @SerializedName("riskScore") val riskScore: Int,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IssueData

    return this.key == other.key
  }

  override fun hashCode(): Int = this.key.hashCode()
}

data class HasAuthenticatedParam(
  @SerializedName("token") val token: String?,
  @SerializedName("apiUrl") val apiUrl: String?,
)

data class SnykTrustedFoldersParams(
  @SerializedName("trustedFolders") val trustedFolders: List<String>
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

data class ScanCommandConfig(
  val preScanCommand: String = "",
  val preScanOnlyReferenceFolder: Boolean = true,
  val postScanCommand: String = "",
  val postScanOnlyReferenceFolder: Boolean = true,
)
