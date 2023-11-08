package snyk.common.lsp

import org.junit.Test

class LanguageServerWrapperTest {

    @Test
    fun initialize() {
        val languageServerWrapper = LanguageServerWrapper("/Users/bdoetsch/workspace/cli/binary-releases/snyk-macos")
        languageServerWrapper.initialize()

    }
}
