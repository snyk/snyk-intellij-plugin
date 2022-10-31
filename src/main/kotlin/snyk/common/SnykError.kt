package snyk.common

data class SnykError(
    val message: String,
    val path: String,
    val code: Int? = null
)
