package snyk.advisor

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.ui.SnykBalloonNotifications
import snyk.advisor.api.PackageInfo
import java.awt.Color
import java.awt.Cursor
import java.awt.Font

class SnykAdvisorEditorLinePainter : EditorLinePainter() {

    private val editorsWithListeners: MutableSet<Editor> = mutableSetOf()
    private val editor2scoredLine: MutableMap<Editor, MutableMap<Int, Pair<PackageInfo, AdvisorPackageManager>>> = mutableMapOf()
    private var selectedScore: Pair<Editor, Int>? = null
    private var currentBalloon: Balloon? = null

    override fun getLineExtensions(project: Project, file: VirtualFile, lineNumber: Int): Collection<LineExtensionInfo>? {
        val settings = getApplicationSettingsStateService()
        if (settings.pluginFirstRun || !settings.advisorEnable) return null

        val packageManager = when (file.name) {
            "package.json" -> AdvisorPackageManager.NPM
            else -> null // todo: replace with python support
        } ?: return null

        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null

        val lineStartElement = psiFile.findElementAt(document.getLineStartOffset(lineNumber)) ?: return null

        val packageName = when (packageManager) {
            AdvisorPackageManager.NPM -> getNpmPackageName(lineStartElement, document, lineNumber)
            else -> null // todo: replace with python support
        } ?: return null

        val editor = PsiEditorUtil.findEditor(lineStartElement) ?: return null

        val info = service<SnykAdvisorModel>().getInfo(project, packageManager, packageName)
        val score = info?.normalizedScore
        if (score == null
            || score > SCORE_THRESHOLD
            || score == 0 // package not_found case
        ) {
            editor2scoredLine[editor]?.remove(lineNumber)
            return null
        }
        editor2scoredLine
            .computeIfAbsent(editor) { mutableMapOf() }
            .putIfAbsent(lineNumber, Pair(info, packageManager))

        if (editorsWithListeners.add(editor)) {
            val listener = AdvisorEditorMouseListener()

            editor.addEditorMouseListener(listener)
            editor.addEditorMouseMotionListener(listener)

            editor.scrollingModel.addVisibleAreaListener {
                currentBalloon?.hide()
                currentBalloon = null
            }
        }

        val attributes = if (selectedScore?.first == editor && selectedScore?.second == lineNumber) {
            getSelectedScoreAttributes()
        } else {
            getNormalAttributes()
        }

        return listOf(
            LineExtensionInfo(LINE_EXTENSION_PREFIX, getWarningIconAttributes()),
            LineExtensionInfo("$LINE_EXTENSION_BODY$score$LINE_EXTENSION_POSTFIX", attributes)
        )
    }

    private fun getNpmPackageName(lineStartElement: PsiElement, document: Document, lineNumber: Int): String? {

        // line should start with PsiWhiteSpace following by JsonProperty
        // or with JsonProperty itself (represented by leaf with type JsonElementTypes.DOUBLE_QUOTED_STRING)
        val name2versionPropertyElement: JsonProperty = when {
            lineStartElement is PsiWhiteSpace &&
                // check for multi-line PsiWhiteSpace element (i.e. few empty lines)
                document.getLineNumber(lineStartElement.textRange.endOffset) == lineNumber
            -> (lineStartElement.nextSibling as? JsonProperty) ?: return null

            lineStartElement is LeafPsiElement &&
                lineStartElement.elementType == JsonElementTypes.DOUBLE_QUOTED_STRING &&
                // see(PsiViewer) Psi representation of package dependencies in package.json, i.e. "adm-zip": "0.4.7"
                lineStartElement.parent is JsonStringLiteral &&
                lineStartElement.parent.parent is JsonProperty
            -> lineStartElement.parent.parent as JsonProperty

            else -> return null
        }

        if (!isInsideNPMDependencies(name2versionPropertyElement)) return null

        // don't show Scores if few packages are on the same line
        val prevName2VersionElement = PsiTreeUtil.getPrevSiblingOfType(name2versionPropertyElement, JsonProperty::class.java)
        if (prevName2VersionElement != null &&
            document.getLineNumber(prevName2VersionElement.textRange.endOffset) == lineNumber) return null

        val nextName2VersionElement = PsiTreeUtil.getNextSiblingOfType(name2versionPropertyElement, JsonProperty::class.java)
        if (nextName2VersionElement != null &&
            document.getLineNumber(nextName2VersionElement.textRange.endOffset) == lineNumber) return null

        // see(PsiViewer) Psi representation of package dependencies in package.json, i.e. "adm-zip": "0.4.7"
        return if (name2versionPropertyElement.firstChild is JsonStringLiteral &&
            name2versionPropertyElement.firstChild.firstChild is LeafPsiElement &&
            (name2versionPropertyElement.firstChild.firstChild as LeafPsiElement).elementType == JsonElementTypes.DOUBLE_QUOTED_STRING
        ) {
            name2versionPropertyElement.firstChild.text.removeSurrounding("\"")
        } else return null

    }

    // see(PsiViewer) Psi representation of "dependencies" in package.json
    private fun isInsideNPMDependencies(element: JsonProperty) =
        element.parent is JsonObject &&
            element.parent.parent is JsonProperty &&
            element.parent.parent.firstChild is JsonStringLiteral &&
            element.parent.parent.firstChild.textMatches("\"dependencies\"")

    inner class AdvisorEditorMouseListener : EditorMouseListener, EditorMouseMotionListener {
        override fun mouseClicked(e: EditorMouseEvent) {
            if (isOverScoreText(e)) {
                val (info, packageManager) =
                    editor2scoredLine[e.editor]?.get(e.logicalPosition.line) ?: return

                val moreDetailsLink = "https://snyk.io/advisor/${packageManager.getUrlName()}/${info.name}"

                val labels = info.labels ?: return
                currentBalloon = SnykBalloonNotifications.showAdvisorMoreDetailsPopup(
                    """<html>
                            <table style="margin-bottom: 10px;">
                                <tr><td>Popularity:</td><td>${labels.popularity}</td></tr>
                                <tr><td>Maintenance:&nbsp;&nbsp;</td><td>${labels.maintenance}</td></tr>
                                <tr><td>Security:</td><td>${labels.security}</td></tr>
                                <tr><td>Community:</td><td>${labels.community}</td></tr>
                            </table>
                            <a href="$moreDetailsLink">More details</a>
                       </html>
                    """.trimIndent(),
                    e.mouseEvent
                )
                selectedScore = Pair(e.editor, e.logicalPosition.line)
            } else {
                currentBalloon?.hide()
                currentBalloon = null
                selectedScore = null
            }
        }

        override fun mouseMoved(e: EditorMouseEvent) {
            val cursor = if (isOverScoreText(e)) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else null
            (e.editor as? EditorEx)?.setCustomCursor(this, cursor)
        }

        private fun isOverScoreText(e: EditorMouseEvent): Boolean {
            // over editable text area
            if (e.isOverText) return false

            // over line with No score shown
            val line = e.logicalPosition.line
            if (editor2scoredLine[e.editor]?.containsKey(line) != true) return false

            // not over Text in LineExtension
            val lineLength = e.editor.document.getLineEndOffset(line) - e.editor.document.getLineStartOffset(line)
            if (e.logicalPosition.column < lineLength + LINE_EXTENSION_PREFIX.length ||
                e.logicalPosition.column > lineLength + LINE_EXTENSION_LENGTH) return false

            return true
        }
    }

    companion object {
        private const val SCORE_THRESHOLD = 70
        private const val LINE_EXTENSION_PREFIX = "    \u26A0" // Unicode ⚠ symbol
        private const val LINE_EXTENSION_BODY = " Advisor Score "
        private const val LINE_EXTENSION_POSTFIX = "/100"

        private const val LINE_EXTENSION_LENGTH =
            (LINE_EXTENSION_PREFIX + LINE_EXTENSION_BODY + "00" + LINE_EXTENSION_POSTFIX).length


        /** see [com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter.getNormalAttributes] */
        private fun getNormalAttributes(): TextAttributes {
            val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
            return if (attributes == null || attributes.foregroundColor == null) {
                TextAttributes(
                    JBColor { if (EditorColorsManager.getInstance().isDarkEditor) Color(0x3d8065) else Gray._135 },
                    null,
                    null,
                    null,
                    Font.ITALIC
                )
            } else attributes
        }

        private fun getWarningIconAttributes(): TextAttributes {
            val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(ConsoleHighlighter.YELLOW)
            return if (attributes == null || attributes.foregroundColor == null) {
                TextAttributes(
                    JBColor { if (EditorColorsManager.getInstance().isDarkEditor) Color.YELLOW.darker() else Color.YELLOW },
                    null,
                    null,
                    null,
                    Font.PLAIN
                )
            } else attributes
        }

        private fun getSelectedScoreAttributes(): TextAttributes = getNormalAttributes().clone().apply {
            fontType = fontType xor Font.BOLD
        }
    }
}
