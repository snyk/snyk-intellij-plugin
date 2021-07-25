package snyk.advisor

enum class AdvisorPackageManager {
    NPM, PYTHON;

    fun getUrlName(): String = when (this) {
        NPM -> "npm-package"
        PYTHON -> "python"
    }
}
