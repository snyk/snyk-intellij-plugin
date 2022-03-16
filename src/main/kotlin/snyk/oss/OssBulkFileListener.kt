package snyk.oss

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.getSnykToolWindowPanel

class OssBulkFileListener : SnykBulkFileListener() {

    private val log = logger<OssBulkFileListener>()

    override fun before(project: Project, events: List<VFileEvent>) {
        val virtualFilesAffected = getAffectedVirtualFiles(
            events,
            classesOfEventsToFilter = listOf(
                VFileCreateEvent::class.java,
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java,
                VFileDeleteEvent::class.java
            )
        )
        //throw RuntimeException()
        val toolWindowPanel = getSnykToolWindowPanel(project)

        if (toolWindowPanel?.currentOssResults != null) {
            val buildFileChanged = virtualFilesAffected
                .filter { supportedBuildFiles.contains(it.name) }
                .find { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
            if (buildFileChanged != null) {
                toolWindowPanel.currentOssResults = null
                log.debug("OSS cached results dropped due to changes in: $buildFileChanged")
            }
        }
    }

    override fun after(project: Project, events: List<VFileEvent>) {
        val virtualFilesAffected = getAffectedVirtualFiles(
            events,
            eventToVirtualFileTransformer = { transformEventToNewVirtualFile(it) },
            classesOfEventsToFilter = listOf(
                VFileCreateEvent::class.java,
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java
            )
        )
        // todo: update OSS cache if new build file created
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
