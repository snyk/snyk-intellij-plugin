package snyk.advisor.buildsystems

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.intellij.lang.annotations.Language

class PythonSupport(private val editor: Editor) {

    // see https://pip.pypa.io/en/stable/cli/pip_install/#requirements-file-format
    fun getPackageName(lineNumber: Int): String? {
        // current line is continuation of previous line -> no package name could be here
        if (lineNumber > 1 && getLineText(lineNumber - 1).endsWith("\\")) {
            return null
        }
        val lineText = getLineText(lineNumber)

        return findPackageName(lineText)
    }

    private fun getLineText(lineNumber: Int): String {
        val document = editor.document
        return document.getText(
            TextRange(
                document.getLineStartOffset(lineNumber),
                document.getLineEndOffset(lineNumber)
            )).trim()
    }

    companion object {
        // ----- begin of pip https://www.python.org/dev/peps/pep-0508/#grammar regex ------

        // alpha-numeric symbol
        @Language("RegExp")
        private const val alphaNum = "A-Za-z0-9"

        // https://www.python.org/dev/peps/pep-0440/#version-specifiers + `(` as described in grammar for `versionspec`
        @Language("RegExp")
        private const val comparingBegin = "=~!<>\\("

        @Language("RegExp")
        private const val extrasBegin = "\\["

        // https://www.python.org/dev/peps/pep-0508/#environment-markers
        @Language("RegExp")
        private const val envMarkerBegin = ";"

        @Language("RegExp")
        private const val commentBegin = " +#"

        // https://pip.pypa.io/en/stable/cli/pip_install/#per-requirement-overrides
        @Language("RegExp")
        private const val overridesBegin = " +--"

        @Language("RegExp")
        private const val lineContinuation = " *\\\\$"

        // end of line or followed by version comparison | extras | env marker
        @Language("RegExp")
        private const val lookAhead =
            "(?=($| *[$comparingBegin$extrasBegin$envMarkerBegin]|$commentBegin|$overridesBegin|$lineContinuation))"

        private val pyPackageNameRegex =
            "^[$alphaNum]([$alphaNum._-]*[$alphaNum])?$lookAhead".toRegex()

        // ---- end of pip regex -----

        fun findPackageName(text: String): String? = pyPackageNameRegex.find(text)?.value
    }
}
