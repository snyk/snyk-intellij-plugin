package snyk.common

enum class ProductType(
    val productSelectionName: String,
    val treeName: String,
    val description: String
) {
    OSS(
        productSelectionName = "Snyk Open Source",
        treeName = "Open Source",
        description = "Find and automatically fix open source vulnerabilities"
    ) {
        override fun getCountText(count: Int, isUniqueCount: Boolean): String =
            getVulnerabilitiesCountText(count, isUniqueCount)
    },
    IAC(
        productSelectionName = "Snyk Infrastructure as Code",
        treeName = "Configuration",
        description = "Find and fix insecure configurations in Terraform and Kubernetes code"
    ) {
        override fun getCountText(count: Int, isUniqueCount: Boolean): String =
            getIssuesCountText(count, isUniqueCount)
    },
    CODE_SECURITY(
        productSelectionName = "Snyk Code Security",
        treeName = "Code Security",
        description = "Find and fix vulnerabilities in your application code in real time"
    ) {
        override fun getCountText(count: Int, isUniqueCount: Boolean): String =
            getVulnerabilitiesCountText(count, isUniqueCount)
    };

    override fun toString(): String = productSelectionName

    abstract fun getCountText(count: Int, isUniqueCount: Boolean = false): String

    protected fun getIssuesCountText(count: Int, isUnique: Boolean = false): String =
        " - $count${if (isUnique) " unique" else ""} issue${if (count > 1) "s" else ""}"

    protected fun getVulnerabilitiesCountText(count: Int, isUnique: Boolean = false): String =
        " - $count${if (isUnique) " unique" else ""} vulnerabilit${if (count > 1) "ies" else "y"}"
}
