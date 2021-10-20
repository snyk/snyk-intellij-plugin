package snyk.iac

data class IacIssue(
    val id: String,
    val title: String,
    val severity: String,
    val publicId: String,
    val documentation: String,
    val lineNumber: Int,
    val issue: String,
    val impact: String,
    val resolve: String?,
    val references: List<String>,
    val path: List<String>
)
