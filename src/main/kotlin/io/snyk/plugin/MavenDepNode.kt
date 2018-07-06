package io.snyk.plugin

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.project.MavenProject

data class MavenDepRoot(val deps: List<MavenDepNode>) {
    companion object {
        fun fromMavenProject(proj: MavenProject): MavenDepRoot = MavenDepRoot(
            proj.dependencyTree.map { MavenDepNode.fromMavenArtifactNode(it) }
        )
    }
}

data class MavenDepNode(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val type: String,
    val classifier: String?,
    val scope: String,
    val deps: List<MavenDepNode>
) {
    companion object {
        fun fromMavenArtifactNode(n: MavenArtifactNode): MavenDepNode = MavenDepNode(
            n.artifact.groupId,
            n.artifact.artifactId,
            if(StringUtil.isEmptyOrSpaces(n.artifact.baseVersion)) n.artifact.version else n.artifact.baseVersion,
            n.artifact.type,
            n.artifact.classifier,
            n.artifact.scope,
            n.dependencies.map { fromMavenArtifactNode(it) }
        )
    }
}