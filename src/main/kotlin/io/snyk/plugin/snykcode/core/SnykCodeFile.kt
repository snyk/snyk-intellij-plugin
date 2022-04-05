package io.snyk.plugin.snykcode.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class SnykCodeFile(val project: Project, val virtualFile: VirtualFile)
