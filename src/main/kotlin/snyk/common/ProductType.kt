package snyk.common

enum class ProductType(
    val productSelectionName: String,
    val treeName: String,
    val description: String
) {
    OSS(
        productSelectionName = "Snyk Open Source",
        treeName = "Open Source Vulnerabilities",
        description = "Find and automatically fix open source vulnerabilities"
    ) {
        override fun getIssuesCountText(count: Int): String = " - $count vulnerabilit${if (count > 1) "ies" else "y"}"
    },
    IAC(
        productSelectionName = "Snyk Infrastructure as Code",
        treeName = "Configuration Issues",
        description = "Find and fix insecure configurations in Terraform and Kubernetes code"
    ),
    CONTAINER(
        productSelectionName = "Snyk Container",
        treeName = "Container Vulnerabilities",
        description = "Find and fix vulnerabilities in container images and Kubernetes applications"
    ) {
        override fun getIssuesCountText(count: Int): String = " - $count vulnerabilit${if (count > 1) "ies" else "y"}"
    },
    CODE_SECURITY(
        productSelectionName = "Snyk Code Security",
        treeName = "Code Security Issues",
        description = "Find and fix vulnerabilities in your application code in real time"
    ),
    CODE_QUALITY(
        productSelectionName = "Snyk Code Quality",
        treeName = "Code Quality Issues",
        description = "Find and fix code quality issues in your application code in real time"
    ),
    ADVISOR(
        productSelectionName = "Snyk Advisor (early preview)",
        treeName = "",
        description = "Discover the health (maintenance, community, popularity & security)\n" +
            "status of your open source packages"
    );

    override fun toString(): String = productSelectionName

    open fun getIssuesCountText(count: Int): String = " - $count issue${if (count > 1) "s" else ""}"
}
