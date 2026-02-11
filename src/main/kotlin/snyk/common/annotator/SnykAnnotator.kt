package snyk.common.annotator

import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language.ANY
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.toLanguageServerURI
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.Icon
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.concurrency.runAsync
import snyk.common.AnnotatorCommon
import snyk.common.ProductType
import snyk.common.annotator.SnykAnnotationAttributeKey.critical
import snyk.common.annotator.SnykAnnotationAttributeKey.high
import snyk.common.annotator.SnykAnnotationAttributeKey.low
import snyk.common.annotator.SnykAnnotationAttributeKey.medium
import snyk.common.annotator.SnykAnnotationAttributeKey.unknown
import snyk.common.annotator.SnykAnnotator.SnykAnnotation
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.RangeConverter
import snyk.common.lsp.ScanIssue

// Reduced timeout to prevent long UI freezes when LS is busy
private const val CODEACTION_TIMEOUT = 2L

typealias SnykAnnotationInput = Pair<PsiFile, Map<Range, List<ScanIssue>>>

typealias SnykAnnotationList = List<SnykAnnotation>

abstract class SnykAnnotator(private val product: ProductType) :
  ExternalAnnotator<SnykAnnotationInput, SnykAnnotationList>(), Disposable, DumbAware {
  private val lineMarkerProviderDescriptor: SnykLineMarkerProvider = getLineMarkerProvider()

  val logger = logger<SnykAnnotator>()
  protected var disposed = false
    get() {
      return ApplicationManager.getApplication().isDisposed || field
    }

  fun isDisposed() = disposed

  override fun dispose() {
    disposed = true
  }

  class SnykAnnotation(
    val issue: ScanIssue,
    val annotationSeverity: HighlightSeverity,
    val annotationMessage: String,
    val range: TextRange,
    val intentionActions: MutableList<IntentionAction> = mutableListOf(),
    var gutterIconRenderer: GutterIconRenderer? = null,
  )

  override fun collectInformation(file: PsiFile): SnykAnnotationInput? {
    if (disposed) return null
    val project = file.project
    if (!LanguageServerWrapper.getInstance(project).isInitialized) return null
    val annotatorCommon = project.service<AnnotatorCommon>()
    val map =
      getIssuesForFile(file)
        .filter { annotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }
        .sortedByDescending { it.getSeverityAsEnum() }
        .groupBy { it.range }
        .toMap()

    return Pair(file, map)
  }

  override fun doAnnotate(initialInfo: SnykAnnotationInput): SnykAnnotationList {
    if (disposed) return emptyList()
    val psiFile = initialInfo.first
    val project = psiFile.project
    if (!LanguageServerWrapper.getInstance(project).isInitialized) return emptyList()
    val annotatorCommon = project.service<AnnotatorCommon>()

    val gutterIconEnabled = LineMarkerSettings.getSettings().isEnabled(lineMarkerProviderDescriptor)

    annotatorCommon.prepareAnnotate(psiFile)

    val codeActions =
      initialInfo.second
        .map { entry ->
          entry.key to getCodeActions(psiFile, entry.key).map { it.right }.sortedBy { it.title }
        }
        .toMap()

    val annotations = mutableListOf<SnykAnnotation>()
    initialInfo.second.forEach { entry ->
      val textRange = RangeConverter.convertToTextRange(psiFile, entry.key)
      if (textRange == null || textRange.isEmpty) {
        logger.warn("Invalid range for range: $textRange")
        return@forEach
      }
      annotations.addAll(doAnnotateIssue(entry, textRange, gutterIconEnabled, codeActions))
    }
    return annotations.sortedByDescending { it.issue.getSeverityAsEnum() }
  }

  private fun doAnnotateIssue(
    entry: Map.Entry<Range, List<ScanIssue>>,
    textRange: TextRange,
    gutterIconEnabled: Boolean,
    codeActions: Map<Range, List<CodeAction>>,
  ): List<SnykAnnotation> {
    val gutterIcons: MutableSet<TextRange> = mutableSetOf()
    val annotations = mutableListOf<SnykAnnotation>()
    entry.value.forEach { issue ->
      val highlightSeverity = issue.getSeverityAsEnum().getHighlightSeverity()
      val annotationMessage = issue.annotationMessage()

      val detailAnnotation = SnykAnnotation(issue, highlightSeverity, annotationMessage, textRange)

      val gutterIconRenderer =
        if (gutterIconEnabled && !gutterIcons.contains(textRange)) {
          gutterIcons.add(textRange)
          SnykShowDetailsGutterRenderer(detailAnnotation)
        } else {
          null
        }

      val languageServerIntentionActions =
        codeActions[entry.key]?.let { range -> getCodeActionsAsIntentionActions(issue, range) }
          ?: emptyList()

      detailAnnotation.gutterIconRenderer = gutterIconRenderer
      detailAnnotation.intentionActions.add(ShowDetailsIntentionAction(annotationMessage, issue))
      detailAnnotation.intentionActions.addAll(languageServerIntentionActions)
      annotations.add(detailAnnotation)
    }
    return annotations
  }

  override fun apply(
    psiFile: PsiFile,
    annotationResult: SnykAnnotationList,
    holder: AnnotationHolder,
  ) {
    if (disposed) return
    val project = psiFile.project
    if (!LanguageServerWrapper.getInstance(project).isInitialized) return

    annotationResult.forEach { annotation ->
      if (!annotation.range.isEmpty) {
        val annoBuilder =
          holder
            .newAnnotation(annotation.annotationSeverity, annotation.annotationMessage)
            .range(annotation.range)
            .textAttributes(getTextAttributeKeyBySeverity(annotation.issue.getSeverityAsEnum()))

        annotation.intentionActions.forEach { annoBuilder.withFix(it) }

        if (annotation.gutterIconRenderer != null) {
          annoBuilder.gutterIconRenderer(SnykShowDetailsGutterRenderer(annotation))
        }

        annoBuilder.create()
      }
    }
  }

  private fun getCodeActionsAsIntentionActions(
    issue: ScanIssue,
    codeActions: List<CodeAction>,
  ): MutableList<IntentionAction> {
    val addedIntentionActions = mutableListOf<IntentionAction>()

    codeActions
      .filter { action ->
        val diagnosticCode = action.diagnostics?.get(0)?.code?.left
        val ruleId = issue.ruleId()
        diagnosticCode == ruleId
      }
      .forEach { action -> addedIntentionActions.add(CodeActionIntention(issue, action, product)) }

    return addedIntentionActions
  }

  private fun getCodeActions(file: PsiFile, range: Range): List<Either<Command, CodeAction>> {
    val lsWrapper = LanguageServerWrapper.getInstance(file.project)
    // Early return if LS is not ready - prevents blocking calls during initialization
    if (lsWrapper.isDisposed() || !lsWrapper.isInitialized) return emptyList()

    val params =
      CodeActionParams(
        TextDocumentIdentifier(file.virtualFile.toLanguageServerURI()),
        range,
        CodeActionContext(emptyList()),
      )
    val codeActions =
      try {
        lsWrapper.languageServer.textDocumentService
          .codeAction(params)
          .get(CODEACTION_TIMEOUT, TimeUnit.SECONDS) ?: emptyList()
      } catch (_: TimeoutException) {
        logger.info("Timeout fetching code actions for range: $range")
        emptyList()
      }
    return codeActions
  }

  private fun getLineMarkerProvider(): SnykLineMarkerProvider {
    val lineMarkerProviderDescriptor: SnykLineMarkerProvider =
      LineMarkerProviders.getInstance()
        .allForLanguage(ANY)
        .stream()
        .filter { p -> p is SnykLineMarkerProvider }
        .findFirst()
        .orElse(null) as SnykLineMarkerProvider
    return lineMarkerProviderDescriptor
  }

  private fun getTextAttributeKeyBySeverity(severity: Severity): TextAttributesKey {
    return when (severity) {
      Severity.UNKNOWN -> unknown
      Severity.LOW -> low
      Severity.MEDIUM -> medium
      Severity.HIGH -> high
      Severity.CRITICAL -> critical
    }
  }

  private fun getIssuesForFile(psiFile: PsiFile): Set<ScanIssue> =
    getSnykCachedResultsForProduct(psiFile.project, product)
      ?.filter {
        val virtualFile = it.key.virtualFile
        // Don't call isInContent here - it uses ReadAction.compute which can block EDT
        // The file is already from our cache which only contains content files
        virtualFile == psiFile.virtualFile
      }
      ?.map { it.value }
      ?.flatten()
      ?.toSet() ?: emptySet()

  class SnykShowDetailsGutterRenderer(val annotation: SnykAnnotation) : GutterIconRenderer() {
    override fun equals(other: Any?): Boolean {
      return annotation == other
    }

    override fun hashCode(): Int {
      return annotation.hashCode()
    }

    override fun getIcon(): Icon {
      return SnykIcons.getSeverityIcon(annotation.issue.getSeverityAsEnum())
    }

    override fun getClickAction(): AnAction? {
      val intention =
        annotation.intentionActions.firstOrNull { it is ShowDetailsIntentionAction } ?: return null
      return getShowDetailsNavigationAction(intention as ShowDetailsIntentionAction)
    }

    private fun getShowDetailsNavigationAction(intention: ShowDetailsIntentionAction) =
      object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
          runAsync {
            val project = annotation.issue.project ?: return@runAsync
            val toolWindowPanel = getSnykToolWindowPanel(project) ?: return@runAsync
            intention.selectNodeAndDisplayDescription(toolWindowPanel)
          }
        }
      }

    override fun getTooltipText(): String {
      return annotation.annotationMessage
    }

    override fun getAccessibleName(): String {
      return annotation.annotationMessage
    }

    override fun isNavigateAction(): Boolean {
      return true
    }

    override fun isDumbAware(): Boolean {
      return true
    }
  }
}
