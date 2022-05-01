package snyk.oss

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.getSnykCachedResults

class OssBulkFileListener : SnykBulkFileListener() {

    private val log = logger<OssBulkFileListener>()

    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        dropOssCacheIfNeeded(project, virtualFilesAffected)
    }

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        dropOssCacheIfNeeded(project, virtualFilesAffected)
    }

    private fun dropOssCacheIfNeeded(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        val snykCachedResults = getSnykCachedResults(project)
        if (snykCachedResults?.currentOssResults != null) {
            val buildFileChanged = virtualFilesAffected
                .filter { supportedBuildFiles.contains(it.name) }
                .find { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
            if (buildFileChanged != null) {
                snykCachedResults.currentOssResults = null
                log.debug("OSS cached results dropped due to changes in: $buildFileChanged")
            }
        }
    }

    companion object {
        // see https://github.com/snyk/snyk/blob/master/src/lib/detect.ts#L10
        private val supportedBuildFiles = listOf(
            "yarn.lock",
            "package-lock.json",
            "package.json",
            "Gemfile",
            "Gemfile.lock",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "build.sbt",
            "Pipfile",
            "requirements.txt",
            "Gopkg.lock",
            "go.mod",
            "vendor.json",
            "project.assets.json",
            "project.assets.json",
            "packages.config",
            "paket.dependencies",
            "composer.lock",
            "Podfile",
            "Podfile.lock",
            "pyproject.toml",
            "poetry.lock"
        )
    }
}
