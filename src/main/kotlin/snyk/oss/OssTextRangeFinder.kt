package snyk.oss

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

@Service
class OssTextRangeFinder {
    private val availableFinders: MutableList<(psiFile: PsiFile, vulnerability: Vulnerability) -> TextRange> = mutableListOf()

    fun registerFinder(finder: (psiFile: PsiFile, vulnerability: Vulnerability) -> TextRange) {
        availableFinders.add(finder)
    }

    fun findTextRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange? = availableFinders.asSequence()
        .map { it(psiFile, vulnerability) }
        .firstOrNull { it != TextRange.EMPTY_RANGE }
}
