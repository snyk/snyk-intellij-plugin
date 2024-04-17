package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.snykcode.core.SnykCodeFile
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class OpenFileLoadHandlerGenerator(snykCodeFile: SnykCodeFile) {
    private val project = snykCodeFile.project
    private val virtualFile = snykCodeFile.virtualFile

    fun openFile(value: String): JBCefJSQuery.Response {
        val values = value.replace("\n", "").split(":")
        val startLine = values[1].toInt()
        val endLine = values[2].toInt()
        val startCharacter = values[3].toInt()
        val endCharacter = values[4].toInt()

        ApplicationManager.getApplication().invokeLater {
            val document = virtualFile.getDocument()
            val startLineStartOffset = document?.getLineStartOffset(startLine) ?: 0
            val startOffset = startLineStartOffset + (startCharacter)
            val endLineStartOffset = document?.getLineStartOffset(endLine) ?: 0
            val endOffset = endLineStartOffset + endCharacter - 1

            navigateToSource(project, virtualFile, startOffset, endOffset)
        }

        return JBCefJSQuery.Response("success")
    }

    fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val openFileQuery = JBCefJSQuery.create(jbCefBrowser)
        openFileQuery.addHandler { openFile(it) }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    browser.executeJavaScript(
                        "window.openFileQuery = function(value) {" +
                            openFileQuery.inject("value") +
                            "};",
                        browser.url, 0
                    );

                    browser.executeJavaScript(
                        """
                    const dataFlowFilePaths = document.getElementsByClassName('data-flow-clickable-row')
                    for (let i = 0; i < dataFlowFilePaths.length; i++) {
                        const dataFlowFilePath = dataFlowFilePaths[i]
                        dataFlowFilePath.addEventListener('click', function (e) {
                          e.preventDefault()
                          window.openFileQuery(dataFlowFilePath.getAttribute("file-path")+":"+dataFlowFilePath.getAttribute("start-line")+":"+dataFlowFilePath.getAttribute("end-line")+":"+dataFlowFilePath.getAttribute("start-character")+":"+dataFlowFilePath.getAttribute("end-character"));
                        });
                    }""",
                        browser.url, 0
                    );

                    val isDarkTheme = EditorColorsManager.getInstance().isDarkEditor
                    val themeScript = if (isDarkTheme) {
                        """
                        const style = getComputedStyle(document.documentElement);
                        document.documentElement.style.setProperty('--text-color', style.getPropertyValue('--text-color-dark'));
                        document.documentElement.style.setProperty('--background-color', style.getPropertyValue('--background-color-dark'));
                        document.documentElement.style.setProperty('--link-color', style.getPropertyValue('--link-color-dark'));
                        document.documentElement.style.setProperty('--info-text-color', style.getPropertyValue('--info-text-color-dark'));
                        document.documentElement.style.setProperty('--disabled-text-color', style.getPropertyValue('--disabled-text-color-dark'));
                        document.documentElement.style.setProperty('--selected-text-color', style.getPropertyValue('--selected-text-color-dark'));
                        document.documentElement.style.setProperty('--error-text-color', style.getPropertyValue('--error-text-color-dark'));
                        document.documentElement.style.setProperty('--data-flow-file-color', style.getPropertyValue('--data-flow-file-color-dark'));
                        document.documentElement.style.setProperty('--example-line-added-color', style.getPropertyValue('--example-line-added-color-dark'));
                        document.documentElement.style.setProperty('--example-line-removed-color', style.getPropertyValue('--example-line-removed-color-dark'));
                        document.documentElement.style.setProperty('--tabs-bottom-color', style.getPropertyValue('--tabs-bottom-color-dark'));
                        document.documentElement.style.setProperty('--tab-item-color', style.getPropertyValue('--tab-item-color-dark'));
                        document.documentElement.style.setProperty('--tab-item-hover-color', style.getPropertyValue('--tab-item-hover-color-dark'));
                        document.documentElement.style.setProperty('--tab-item-icon-color', style.getPropertyValue('--tab-item-icon-color-dark'));
                        document.documentElement.style.setProperty('--scrollbar-thumb-color', style.getPropertyValue('--scrollbar-thumb-color-dark'));
                        document.documentElement.style.setProperty('--scrollbar-color', style.getPropertyValue('--scrollbar-color-dark'));
                          """
                    } else {
                        """
                        const style = getComputedStyle(document.documentElement);
                        document.documentElement.style.setProperty('--text-color', style.getPropertyValue('--text-color-light'));
                        document.documentElement.style.setProperty('--background-color', style.getPropertyValue('--background-color-light'));
                        document.documentElement.style.setProperty('--link-color', style.getPropertyValue('--link-color-light'));
                        document.documentElement.style.setProperty('--info-text-color', style.getPropertyValue('--info-text-color-light'));
                        document.documentElement.style.setProperty('--disabled-text-color', style.getPropertyValue('--disabled-text-color-light'));
                        document.documentElement.style.setProperty('--selected-text-color', style.getPropertyValue('--selected-text-color-light'));
                        document.documentElement.style.setProperty('--error-text-color', style.getPropertyValue('--error-text-color-light'));
                        document.documentElement.style.setProperty('--error-text-color', style.getPropertyValue('--data-flow-file-color-light'));
                        document.documentElement.style.setProperty('--data-flow-file-color', style.getPropertyValue('--data-flow-file-color-light'));
                        document.documentElement.style.setProperty('--example-line-added-color', style.getPropertyValue('--example-line-added-color-light'));
                        document.documentElement.style.setProperty('--example-line-removed-color', style.getPropertyValue('--example-line-removed-color-light'));
                        document.documentElement.style.setProperty('--tabs-bottom-color', style.getPropertyValue('--tabs-bottom-color-light'));
                        document.documentElement.style.setProperty('--tab-item-color', style.getPropertyValue('--tab-item-color-light'));
                        document.documentElement.style.setProperty('--tab-item-hover-color', style.getPropertyValue('--tab-item-hover-color-light'));
                        document.documentElement.style.setProperty('--tab-item-icon-color', style.getPropertyValue('--tab-item-icon-color-light'));
                        document.documentElement.style.setProperty('--scrollbar-thumb-color', style.getPropertyValue('--scrollbar-thumb-color-light'));
                        document.documentElement.style.setProperty('--scrollbar-color', style.getPropertyValue('--scrollbar-color-light'));
                        """
                    }
                    browser.executeJavaScript(themeScript, browser.url, 0)
                }
            }
        }
    }
}

