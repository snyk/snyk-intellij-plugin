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
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocumentManager
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.toLanguageServerURI
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.InlineValueContext
import org.eclipse.lsp4j.InlineValueParams
import org.eclipse.lsp4j.InlineValueText
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import snyk.common.lsp.LanguageServerWrapper

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
    val virtualFile = psiFile?.virtualFile
    if (psiFile == null || virtualFile == null || psiFile is PsiCompiledElement) {
      return
    }

    if (editor !is EditorEx) {
      return
    }

    val snykFile = SnykFile(project, virtualFile)

    editor.registerLineExtensionPainter(
      EditorLineEndingProvider(editor, snykFile)::getLineExtension
    )
  }

  class EditorLineEndingProvider(val editor: Editor, val snykFile: SnykFile) : Disposable {
    companion object {
      private val attributeKey: TextAttributesKey =
        DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND
      val attributes: TextAttributes =
        EditorColorsManager.getInstance().globalScheme.getAttributes(attributeKey)
      val ctx = InlineValueContext(0, Range())
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

    val project = editor.project
    val document = editor.document
    private val snykCachedResults = project?.let { getSnykCachedResults(it) }
    val languageServerWrapper = project?.let { LanguageServerWrapper.getInstance(it) }
    val logger =
      Logger.getInstance(this::class.java).apply {
        // tie log level to language server log level
        if (languageServerWrapper?.logger?.isDebugEnabled == true) this.setLevel(LogLevel.DEBUG)
        if (languageServerWrapper?.logger?.isTraceEnabled == true) this.setLevel(LogLevel.TRACE)
      }

    // Cache for inline values - avoids blocking EDT calls during painting
    private val inlineValueCache = mutableMapOf<Int, String>()
    // Track pending requests to avoid duplicate fetches
    private val pendingLines = mutableSetOf<Int>()
    // Limit concurrent requests to avoid flooding LS
    private val maxPendingRequests = 5

    fun getLineExtension(line: Int): MutableCollection<out LineExtensionInfo> {
      // only OSS has inline values right now
      val hasResults = snykCachedResults?.currentOSSResultsLS?.get(snykFile)?.isNotEmpty() ?: false
      if (
        (disposed || languageServerWrapper == null) ||
          !languageServerWrapper.isInitialized ||
          !hasResults
      )
        return mutableSetOf()

      // Return cached value if available (non-blocking on EDT)
      val cachedText = inlineValueCache[line]
      if (cachedText != null) {
        return if (cachedText.isNotEmpty()) {
          mutableListOf(LineExtensionInfo("\t$cachedText", attributes))
        } else {
          mutableSetOf()
        }
      }

      // Schedule async fetch if not already pending and under rate limit
      if (!pendingLines.contains(line) && pendingLines.size < maxPendingRequests) {
        pendingLines.add(line)
        fetchInlineValueAsync(line)
      }

      // Return empty for now - async fetch will trigger repaint when done
      return mutableSetOf()
    }

    private fun fetchInlineValueAsync(line: Int) {
      if (disposed || languageServerWrapper == null || project == null) return

      // Run entire LS call on background thread - even creating the future can block
      org.jetbrains.concurrency.runAsync {
        if (disposed || project.isDisposed) {
          pendingLines.remove(line)
          return@runAsync
        }

        try {
          val lineEndOffset = document.getLineEndOffset(line)
          val lineStartOffset = document.getLineStartOffset(line)
          val range = Range(Position(line, 0), Position(line, lineEndOffset - lineStartOffset))
          val params =
            InlineValueParams(
              TextDocumentIdentifier(snykFile.virtualFile.toLanguageServerURI()),
              range,
              ctx,
            )

          val result =
            languageServerWrapper.languageServer.textDocumentService
              .inlineValue(params)
              .get(100, java.util.concurrent.TimeUnit.MILLISECONDS)

          if (disposed) return@runAsync

          val text =
            if (result != null && result.isNotEmpty()) {
              @Suppress("UNCHECKED_CAST")
              val eitherList = result as List<Either<InlineValueText, *>>
              eitherList.firstOrNull()?.left?.text ?: ""
            } else {
              ""
            }

          inlineValueCache[line] = text
          pendingLines.remove(line)

          // Trigger repaint on EDT if we got a non-empty result
          if (text.isNotEmpty() && !disposed && editor.component.isShowing) {
            ApplicationManager.getApplication().invokeLater {
              if (!disposed && !project.isDisposed) {
                editor.component.repaint()
              }
            }
          }
        } catch (ignored: Exception) {
          pendingLines.remove(line)
          inlineValueCache[line] = ""
        }
      }
    }
  }
}
