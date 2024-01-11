package snyk.container

import com.intellij.openapi.vfs.VirtualFile

data class KubernetesWorkloadImage(
    val image: String,
    val virtualFile: VirtualFile,
    val lineNumber: Int = 0,
    val lineStartOffset: Int = 0,
)
