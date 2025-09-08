@file:Suppress("UnstableApiUsage")

package snyk.common.lsp.hovers

import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.isDocumentationHoverEnabled
import io.snyk.plugin.toLanguageServerURI
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SnykDocumentationTargetPointer(private val documentationTarget: DocumentationTarget) :
    Pointer<DocumentationTarget> {

    override fun dereference(): DocumentationTarget {
        return documentationTarget
    }
}

class LSDocumentationTargetProvider : DocumentationTargetProvider, Disposable {
    private var disposed = false
        get() {
            return SnykPluginDisposable.getInstance().isDisposed() || field
        }

    fun isDisposed() = disposed

    override fun documentationTargets(file: PsiFile, offset: Int): MutableList<out DocumentationTarget> {
        val languageServerWrapper = LanguageServerWrapper.getInstance(file.project)
        val emptyReturnList = mutableListOf<DocumentationTarget>()
        if (disposed || !languageServerWrapper.isInitialized || !isDocumentationHoverEnabled()) return emptyReturnList

        val lineNumber = file.viewProvider.document.getLineNumber(offset)
        val lineStartOffset = file.viewProvider.document.getLineStartOffset(lineNumber)
        val virtualFile: VirtualFile? = try {
            file.virtualFile
        } catch (_: NullPointerException) {
            // don't do anything
            null
        }

        if (virtualFile == null) {
            return emptyReturnList
        }

        val hoverParams = HoverParams(
            TextDocumentIdentifier(virtualFile.toLanguageServerURI()),
            Position(lineNumber, offset - lineStartOffset)
        )
        try {
            val hover =
                languageServerWrapper.languageServer.textDocumentService.hover(hoverParams).get(5, TimeUnit.SECONDS)
            if (hover == null || hover.contents.right.value.isEmpty()) return emptyReturnList
            return mutableListOf(SnykDocumentationTarget(hover))
        } catch (ignored: TimeoutException) {
            return emptyReturnList
        }
    }

    inner class SnykDocumentationTarget(private val hover: Hover) : DocumentationTarget {
        override fun computeDocumentation(): DocumentationResult? {
            val htmlText = hover.contents.right.value
            if (htmlText.isEmpty()) {
                return null
            }
            return DocumentationResult.documentation(htmlText)
        }

        override fun computePresentation(): TargetPresentation {
            return TargetPresentation.builder("Snyk Security").icon(SnykIcons.TOOL_WINDOW).presentation()
        }

        override fun createPointer(): Pointer<out DocumentationTarget> {
            return SnykDocumentationTargetPointer(this)
        }
    }


    override fun dispose() {
        disposed = true
    }
}
