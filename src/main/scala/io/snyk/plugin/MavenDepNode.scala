package io.snyk.plugin

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.project.MavenProject

import scala.collection.JavaConverters._
import io.circe.syntax._

import scala.beans.BeanProperty
import java.{util => ju}

import io.circe.{Decoder, Encoder}
import io.circe.derivation.{deriveDecoder, deriveEncoder}

object MavenDepNode {
  def fromMavenArtifactNode(n: MavenArtifactNode): MavenDepNode = {
    println(s"dep tree for node: ${n.getDependencies}")

    MavenDepNode(
      n.getArtifact.getGroupId,
      n.getArtifact.getArtifactId,
      if(StringUtil.isEmptyOrSpaces(n.getArtifact.getBaseVersion)) n.getArtifact.getVersion
      else n.getArtifact.getBaseVersion,
      n.getArtifact.getType,
      Option(n.getArtifact.getClassifier),
      Option(n.getArtifact.getScope),
      n.getDependencies.asScala.map { fromMavenArtifactNode }
    )
  }

  def fromMavenProject(proj: MavenProject): MavenDepNode = {
    println(s"dep tree for project: ${proj.getDependencyTree}")
    MavenDepNode(
      proj.getMavenId.getGroupId,
      proj.getMavenId.getArtifactId,
      proj.getMavenId.getVersion,
      proj.getPackaging,
      None,
      None,
      proj.getDependencyTree.asScala.map { MavenDepNode.fromMavenArtifactNode }
    )
  }

  case class JsonForm(
    groupId: String,
    artifactId: String,
    packaging: String,
    version: String,
    name: String,
    dependencies: Map[String, JsonForm],
    scope: Option[String]
  )

  object JsonForm {
    def apply(mdn: MavenDepNode): JsonForm = JsonForm(
      mdn.groupId,
      mdn.artifactId,
      mdn.packaging,
      mdn.version,
      mdn.name,
      mdn.depsMap.mapValues(apply),
      mdn.scope

    )
  }

  case class RootJsonForm(
    groupId: String,
    artifactId: String,
    packaging: String,
    version: String,
    name: String,
    dependencies: Map[String, JsonForm],
    packageFormatVersion: String = "mvn:0.0.1",
    meta: Map[String, String] = Map("node" -> "v8.11.3"),
    `type`: String = "maven"
  )

  object RootJsonForm {
    def apply(mdn: MavenDepNode): RootJsonForm = RootJsonForm(
      mdn.groupId,
      mdn.artifactId,
      mdn.packaging,
      mdn.version + "-BUST1",
      mdn.name,
      mdn.depsMap.mapValues(JsonForm.apply)
    )
  }

  case class JavaForm(
    @BeanProperty groupId: String,
    @BeanProperty artifactId: String,
    @BeanProperty packaging: String,
    @BeanProperty version: String,
    @BeanProperty classifier: String,
    @BeanProperty scope: String,
    @BeanProperty name: String,
    @BeanProperty deps: ju.List[JavaForm]
  )

  object JavaForm {
    def apply(mdn: MavenDepNode): JavaForm = JavaForm(
      mdn.groupId,
      mdn.artifactId,
      mdn.packaging,
      mdn.version,
      mdn.classifier.orNull,
      mdn.scope.orNull,
      mdn.name,
      mdn.deps.map(apply).asJava
    )
  }

  implicit val decoderJsonForm: Decoder[JsonForm] = deriveDecoder
  implicit val decoderRootJsonForm: Decoder[RootJsonForm] = deriveDecoder
  implicit val encoderJsonForm: Encoder[JsonForm] = deriveEncoder
  implicit val encoderRootJsonForm: Encoder[RootJsonForm] = deriveEncoder

}

case class MavenDepNode(
  groupId: String,
  artifactId: String,
  version: String,
  packaging: String,
  classifier: Option[String],
  scope: Option[String],
  deps: Seq[MavenDepNode]
) {
  import MavenDepNode._

  val name = s"$groupId:$artifactId"
  val depsMap = deps.map(x => x.name -> x).toMap

  def javaForm = JavaForm(this)

  def toJsonString: String = {
    println("We're doing this thing!")
    JsonForm(this).asJson.spaces2
  }

  def toRootJsonString: String = {
    println("We're doing this thing!")
    RootJsonForm(this).asJson.spaces2
  }

}
