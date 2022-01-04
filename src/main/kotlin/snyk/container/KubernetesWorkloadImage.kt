package snyk.container

import com.intellij.psi.PsiFile

data class KubernetesWorkloadImage(val image: String, val psiFile: PsiFile, val lineNumber: Int = 0)
