package snyk.common.lsp

import org.junit.Test

class LSPClientWrapperTest {

    @Test
    fun initialize() {
        val lspClientWrapper = LSPClientWrapper("/Users/bdoetsch/workspace/cli/binary-releases/snyk-macos")
        lspClientWrapper.initialize()

    }
}
