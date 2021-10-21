package snyk.iac

data class IacIssue(
    val id: String,
    val title: String = "",
    val severity: String = "",
    val publicId: String = id,
    val documentation: String = "",
    val lineNumber: Int = 0,
    val issue: String = id,
    val impact: String = "",
    val resolve: String? = null,
    val references: List<String> = emptyList(),
    val path: List<String> = emptyList()
)
