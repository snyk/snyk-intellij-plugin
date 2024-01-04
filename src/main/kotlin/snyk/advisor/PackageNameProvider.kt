package snyk.advisor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import snyk.advisor.buildsystems.NpmSupport
import snyk.advisor.buildsystems.PythonSupport

class PackageNameProvider(private val editor: Editor, private val packageManager: AdvisorPackageManager) {

    fun getPackageName(psiFile: PsiFile, lineNumber: Int): Pair<String?, AdvisorPackageManager>? {
        // sanity checks, examples taken from ImageOrColorPreviewManager.registerListeners
        val project = editor.project
        if (project == null || project.isDisposed) {
            return null
        }

        if (lineNumber < 0 || (editor.document.lineCount - 1) < lineNumber) {
            log.warn(
                "Line number $lineNumber is out of range " +
                    "[0:${editor.document.lineCount - 1}] at ${psiFile.virtualFile.path}"
            )
            return Pair(null, packageManager)
        }
        val packageName = when (packageManager) {
            AdvisorPackageManager.NPM -> NpmSupport(editor).getPackageName(lineNumber)
            AdvisorPackageManager.PYTHON -> PythonSupport(editor).getPackageName(lineNumber)
        }

        return Pair(packageName, packageManager)
    }

    companion object {
        val log = logger<PackageNameProvider>()
    }
}
