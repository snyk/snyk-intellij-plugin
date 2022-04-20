package snyk.advisor

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import io.snyk.plugin.analytics.getEcosystem
import io.snyk.plugin.getSnykAdvisorModel
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotifications
import snyk.advisor.api.PackageInfo
import snyk.analytics.HealthScoreIsClicked
import java.awt.Color
import java.awt.Cursor
import java.awt.Font

class AdvisorScoreProvider(
    private val editor: Editor
) {
    private val scoredLines: MutableMap<Int, Pair<PackageInfo, AdvisorPackageManager>> = mutableMapOf()
    private var selectedScore: Int? = null
    private var currentBalloon: Balloon? = null
    private var currentCursor: Cursor? = null
    private var editorListener: AdvisorEditorListener? = null

    private val packageNameProvider = PackageNameProvider(editor)

    fun getLineExtensions(lineNumber: Int): Collection<LineExtensionInfo> {
        val settings = pluginSettings()
        if (settings.pluginFirstRun || !settings.advisorEnable) {
            return resetAndReturnEmptyList()
        }

        val (packageName, packageManager) = packageNameProvider.getPackageName(lineNumber)
            ?: return resetAndReturnEmptyList()

        val info = packageName?.let {
            getSnykAdvisorModel().getInfo(editor.project, packageManager, it)
        }
        val score = info?.normalizedScore
        if (score == null ||
            score >= SCORE_THRESHOLD ||
            score <= 0 // package not_found case
        ) {
            scoredLines.remove(lineNumber)
            // no Listeners needed if no scores shown
            return if (scoredLines.isNotEmpty()) emptyList() else resetAndReturnEmptyList()
        } else {
            scoredLines.putIfAbsent(lineNumber, Pair(info, packageManager))
            // init Listeners if not done yet
            if (editorListener == null) {
                editorListener = AdvisorEditorListener()
                editor.addEditorMouseListener(editorListener!!)
                editor.addEditorMouseMotionListener(editorListener!!)
                editor.scrollingModel.addVisibleAreaListener(editorListener!!)
            }
        }

        val attributes = if (selectedScore == lineNumber) {
            getSelectedScoreAttributes()
        } else {
            getNormalAttributes()
        }

        return listOf(
            LineExtensionInfo(LINE_EXTENSION_PREFIX, getWarningIconAttributes()),
            LineExtensionInfo("$LINE_EXTENSION_BODY$score$LINE_EXTENSION_POSTFIX", attributes)
        )
    }

    private fun resetAndReturnEmptyList(): Collection<LineExtensionInfo> {
        scoredLines.clear()
        selectedScore = null
        currentBalloon?.hide()
        currentBalloon = null
        if (editorListener != null) {
            editor.removeEditorMouseListener(editorListener!!)
            editor.removeEditorMouseMotionListener(editorListener!!)
            editor.scrollingModel.removeVisibleAreaListener(editorListener!!)
            editorListener = null
        }
        return emptyList()
    }

    inner class AdvisorEditorListener : EditorMouseListener, EditorMouseMotionListener, VisibleAreaListener {
        override fun mouseClicked(e: EditorMouseEvent) {
            if (isOverScoreText(e)) {
                val (info, packageManager) =
                    scoredLines[e.logicalPosition.line] ?: return

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
                getSnykAnalyticsService().logHealthScoreIsClicked(
                    HealthScoreIsClicked.builder()
                        .ecosystem(packageManager.getEcosystem())
                        .ide(HealthScoreIsClicked.Ide.JETBRAINS)
                        .packageName(info.name)
                        .build()
                )
                selectedScore = e.logicalPosition.line
            } else {
                currentBalloon?.hide()
                currentBalloon = null
                selectedScore = null
            }
        }

        override fun mouseMoved(e: EditorMouseEvent) {
            val cursor = if (isOverScoreText(e)) handCursor else null
            if (currentCursor != cursor) {
                (e.editor as? EditorEx)?.setCustomCursor(this, cursor)
                currentCursor = cursor
            }
        }

        private fun isOverScoreText(e: EditorMouseEvent): Boolean {
            // over editable text area
            if (e.isOverText) return false

            val line = e.logicalPosition.line
            // over line with No score shown
            if (!scoredLines.containsKey(line)) return false

            if (line >= e.editor.document.lineCount) {
                cleanCacheForRemovedLines()
                return false
            }

            // not over Text in LineExtension
            val lineLength = e.editor.document.getLineEndOffset(line) - e.editor.document.getLineStartOffset(line)
            if (e.logicalPosition.column < lineLength + LINE_EXTENSION_PREFIX.length ||
                e.logicalPosition.column > lineLength + LINE_EXTENSION_LENGTH
            ) return false

            return true
        }

        private fun cleanCacheForRemovedLines() {
            val lineCount = editor.document.lineCount
            scoredLines.entries.removeIf { it.key >= lineCount }
        }

        override fun visibleAreaChanged(e: VisibleAreaEvent) {
            currentBalloon?.hide()
            currentBalloon = null
        }
    }

    companion object {
        private const val SCORE_THRESHOLD = 70
        private const val LINE_EXTENSION_PREFIX = "    \u26A0" // Unicode ⚠ symbol
        private const val LINE_EXTENSION_BODY = " Advisor Score "
        private const val LINE_EXTENSION_POSTFIX = "/100"

        private const val LINE_EXTENSION_LENGTH =
            (LINE_EXTENSION_PREFIX + LINE_EXTENSION_BODY + "00" + LINE_EXTENSION_POSTFIX).length

        private val handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        /** see [com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter.getNormalAttributes] */
        private fun getNormalAttributes(): TextAttributes {
            val attributes = EditorColorsManager.getInstance()
                .globalScheme.getAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
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

        private fun getWarningIconAttributes(): TextAttributes = TextAttributes(
            Color.YELLOW.darker(),
            null,
            null,
            null,
            Font.PLAIN
        )

        private fun getSelectedScoreAttributes(): TextAttributes = getNormalAttributes().clone().apply {
            fontType = fontType xor Font.BOLD
        }
    }
}
