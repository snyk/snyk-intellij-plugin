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
    val resolve: String? = null,
    val references: List<String> = emptyList(),
    val path: List<String> = emptyList()
) {
    var ignored = false
}
