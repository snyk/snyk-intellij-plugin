package snyk.iac

import com.google.gson.annotations.Expose

data class IacIssue(
    val id: String,
    val title: String,
    val severity: String,
    val publicId: String,
    val documentation: String,
    val lineNumber: Int,
    val issue: String,
    val impact: String,
    val resolve: String? = null,
    val references: List<String> = emptyList(),
    val path: List<String> = emptyList(),
    @Expose val obsolete: Boolean = false
) {
    var ignored = false

    // We need the equals / hashcode methods as we don't want to include `obsolete` in our equality checks
    @Suppress("DuplicatedCode") // false positive for generated code
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IacIssue

        if (id != other.id) return false
        if (title != other.title) return false
        if (severity != other.severity) return false
        if (publicId != other.publicId) return false
        if (documentation != other.documentation) return false
        if (lineNumber != other.lineNumber) return false
        if (issue != other.issue) return false
        if (impact != other.impact) return false
        if (resolve != other.resolve) return false
        if (references != other.references) return false
        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + severity.hashCode()
        result = 31 * result + publicId.hashCode()
        result = 31 * result + documentation.hashCode()
        result = 31 * result + lineNumber
        result = 31 * result + issue.hashCode()
        result = 31 * result + impact.hashCode()
        result = 31 * result + (resolve?.hashCode() ?: 0)
        result = 31 * result + references.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }
}
