package io.snyk.plugin.depsource.externalproject

import com.intellij.openapi.externalSystem.model.project.{LibraryDependencyData, ModuleData}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.project.Project
import io.snyk.plugin.datamodel.MiniTree
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

import scala.collection.JavaConverters._
import scala.collection.breakOut

class ExternProj(project: Project) {
  private[this] lazy val epm: ExternalProjectsManager = ExternalProjectsManager.getInstance(project)
  private[this] lazy val pdm: ProjectDataManager = ProjectDataManager.getInstance()

  val gradleInfo: Seq[ExternProjInfo] =
    pdm.getExternalProjectsData(project, GradleConstants.SYSTEM_ID).asScala.map(new ExternProjInfo(_))(breakOut)

  def libDepsToMiniTrees(parent: SDataNode[_]): Seq[MiniTree[LibraryDependencyData]] = {
    parent.children collect {
      case node @ SDataNode(ldd: LibraryDependencyData, children) =>
        MiniTree(ldd, libDepsToMiniTrees(node))
    }
  }

  val gradleSourceSets: Seq[GradleSourceSet] = {
    val modules = gradleInfo.flatMap(_.structure.children) collect {
      case node @ SDataNode(_: ModuleData, _) =>
        node.asInstanceOf[SDataNode[ModuleData]]
    }

    val sourceSetNodes = modules flatMap { module =>
      module.children collect {
        case node @ SDataNode(_: GradleSourceSetData, _) =>
          node.asInstanceOf[SDataNode[GradleSourceSetData]]
      }
    }

    sourceSetNodes map { ss =>
      GradleSourceSet(ss.data.getId, libDepsToMiniTrees(ss))
    }
//    modules collect {
//      case SDataNode(module: ModuleData, c1) =>
//        println(s"module: $module")
//        c1 collect {
//          case SDataNode(sourceSet: GradleSourceSetData, c2) =>
//            println(s"  sourceSet: $sourceSet")
//            c2 collect {
//              case SDataNode(libDep: LibraryDependencyData, c3) =>
//                println(s"    libDep: $libDep")
//                c3 foreach { x => println(s"      $x") }
//            }
//
//        }
//    }
  }

}
