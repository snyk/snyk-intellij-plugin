package snyk.common.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import io.snyk.plugin.toLanguageServerURL
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.InlineValueContext
import org.eclipse.lsp4j.InlineValueParams
import org.eclipse.lsp4j.InlineValueText
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.TimeUnit

class LineEndingEditorFactoryListener : EditorFactoryListener, Disposable {
    private var disposed = false
        get() {
            return ApplicationManager.getApplication().isDisposed || field
        }

    fun isDisposed() = disposed
    override fun dispose() {
        disposed = true
    }

    init {
        Disposer.register(SnykPluginDisposable.getInstance(), this)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        if (disposed) return
        val editor = event.editor

        if (editor.isOneLineMode) {
            return
        }

        val project = editor.project
        if (project == null || project.isDisposed) {
            return
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        if (psiFile == null || psiFile.virtualFile == null || psiFile is PsiCompiledElement) {
            return
        }

        if (editor !is EditorEx) {
            return
        }

        editor.registerLineExtensionPainter(EditorLineEndingProvider(editor, psiFile.virtualFile)::getLineExtension)
    }

    class EditorLineEndingProvider(val editor: Editor, val virtualFile: VirtualFile) : Disposable {
        private val attributeKey: TextAttributesKey = DefaultLanguageHighlighterColors.LINE_COMMENT
        val attributes: TextAttributes = EditorColorsManager.getInstance().globalScheme.getAttributes(attributeKey)

        val logger = Logger.getInstance(this::class.java).apply {
            // tie log level to language server log level
            val languageServerWrapper = LanguageServerWrapper.getInstance()
            if (languageServerWrapper.logger.isDebugEnabled) this.setLevel(LogLevel.DEBUG)
            if (languageServerWrapper.logger.isTraceEnabled) this.setLevel(LogLevel.TRACE)
        }

        private var disposed = false
            get() {
                return ApplicationManager.getApplication().isDisposed || field
            }

        fun isDisposed() = disposed
        override fun dispose() {
            disposed = true
        }

        init {
            Disposer.register(SnykPluginDisposable.getInstance(), this)
        }

        fun getLineExtension(line: Int): MutableCollection<out LineExtensionInfo> {
            if (disposed || !LanguageServerWrapper.getInstance()
                    .ensureLanguageServerInitialized()
            ) return mutableSetOf()

            val document = editor.document
            val lineEndOffset = document.getLineEndOffset(line)
            val lineStartOffset = document.getLineStartOffset(line)
            val range = Range(Position(line, 0), Position(line, lineEndOffset - lineStartOffset))
            val ctx = InlineValueContext(0, range)
            val params = InlineValueParams(TextDocumentIdentifier(virtualFile.toLanguageServerURL()), range, ctx)

            val inlineValue = LanguageServerWrapper.getInstance().languageServer.textDocumentService.inlineValue(params)
            @Suppress("USELESS_CAST") val text = try {
                val result = inlineValue.get(120, TimeUnit.SECONDS)
                if (result != null && result.isNotEmpty()) {
                    // this supposedly unneeded cast is needed as the conversion in lsp4j does not work correctly
                    val firstInlineValue = (result as List<Either<InlineValueText, *>>)[0]
                    firstInlineValue.left.text
                } else {
                    ""
                }
            } catch (ignored: Exception) {
                // ignore error
                ""
            }
            return mutableListOf(LineExtensionInfo("\t\t$text", attributes))
        }

    }
}


