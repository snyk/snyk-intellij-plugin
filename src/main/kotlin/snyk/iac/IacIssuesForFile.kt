package snyk.iac

import com.google.gson.annotations.Expose

data class IacIssuesForFile(
    val infrastructureAsCodeIssues: List<IacIssue>,
    val targetFile: String,
    val targetFilePath: String,
    val packageManager: String,
    @Expose val obsolete: Boolean = false
) {
    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size

    @Suppress("DuplicatedCode")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IacIssuesForFile

        if (infrastructureAsCodeIssues != other.infrastructureAsCodeIssues) return false
        if (targetFile != other.targetFile) return false
        if (targetFilePath != other.targetFilePath) return false
        if (packageManager != other.packageManager) return false

        return true
    }

    override fun hashCode(): Int {
        var result = infrastructureAsCodeIssues.hashCode()
        result = 31 * result + targetFile.hashCode()
        result = 31 * result + targetFilePath.hashCode()
        result = 31 * result + packageManager.hashCode()
        return result
    }

}

/* Real json Example: src/integTest/resources/iac-test-results/infrastructure-as-code-goof.json */
