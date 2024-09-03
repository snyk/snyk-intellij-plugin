import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper

class GenerateAIFixHandler(private val project: Project) {

    fun generateAIFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val aiFixQuery = JBCefJSQuery.create(jbCefBrowser)
        aiFixQuery.addHandler { value ->
            val params = value.split(":")
            val folderURI = params[0]
            val fileURI = params[1]
            val issueID = params[2]

            println("Received folderURI: $folderURI, fileURI: $fileURI, issueID: $issueID")

            LanguageServerWrapper.getInstance().sendCodeFixDiffsCommand(folderURI, fileURI, issueID)
            JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.aiFixQuery) {
                            return;
                        }
                        window.aiFixQuery = function(value) { ${aiFixQuery.inject("value")} };

                        const aiFixButton = document.getElementById('generate-ai-fix');
                        aiFixButton.addEventListener('click', () => {
                            const folderURI = "someFolderURI"; // These should be dynamically set
                            const fileURI = "someFileURI";
                            const issueID = "someIssueID";
                            window.aiFixQuery(folderURI + ":" + fileURI + ":" + issueID);
                        });
                    })();
                    """
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }
}
