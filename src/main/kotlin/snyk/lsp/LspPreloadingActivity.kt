package snyk.lsp

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import org.wso2.lsp4intellij.IntellijLanguageClient
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition

class LspPreloadingActivity : PreloadingActivity() {

    override fun preload(indicator: ProgressIndicator) {
        IntellijLanguageClient.addServerDefinition(
            LSPServerStatusWidgetFactory.commandServerDefinition
        )
    }
}
