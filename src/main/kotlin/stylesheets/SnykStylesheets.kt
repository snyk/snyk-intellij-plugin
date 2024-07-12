package stylesheets

object SnykStylesheets {
    private fun getStylesheet(name: String): String {
        return this::class.java.getResource(name)?.readText()
            ?: ""
    }

    val SnykCodeSuggestion = getStylesheet("/stylesheets/snyk_code_suggestion.css")
    val SnykOSSSuggestion = getStylesheet("/stylesheets/snyk_oss_suggestion.css")
}
