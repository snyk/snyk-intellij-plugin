package io.snyk.plugin.ui.actions

import java.awt.event.MouseEvent
import java.util

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.popup._
import com.intellij.ui.awt.RelativePoint
import icons.MavenIcons
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.model.SnykPluginState
import javax.swing.Icon

import scala.collection.JavaConverters._

class SnykSelectProjectAction(pluginState: SnykPluginState)
  extends AnAction("Re-Scan project with Snyk", null, MavenIcons.MavenProject) {

  def currentSelection = pluginState.selectedProjectId

  class MyPopupStep extends ListPopupStep[String] {
    val projIds = pluginState.rootProjectIds
    override def getValues: util.List[String] = projIds.asJava
    override def isSelectable(value: String): Boolean = true
    override def getIconFor(aValue: String): Icon = MavenIcons.MavenProject
    override def getTextFor(value: String): String = value
    override def getSeparatorAbove(value: String): ListSeparator = null //eugh!
    override def getDefaultOptionIndex: Int = projIds.indexOf(pluginState.selectedProjectId.get)
    override val getTitle: String = "Select a Project"
    override def hasSubstep(selectedValue: String): Boolean = false
    override def canceled(): Unit = {}
    override val isMnemonicsNavigationEnabled: Boolean = false
    override val getMnemonicNavigationFilter: MnemonicNavigationFilter[String] = null //eugh!
    override val isSpeedSearchEnabled: Boolean = false
    override val getSpeedSearchFilter: SpeedSearchFilter[String] = null
    override val isAutoSelectionEnabled: Boolean = false
    override val getFinalRunnable: Runnable = null //eugh!

    override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] = {
      if (selectedValue != pluginState.selectedProjectId.get) {
        pluginState.selectedProjectId := selectedValue
        pluginState.navigateTo("/html/vulns.hbs", ParamSet.Empty)
      }
      PopupStep.FINAL_CHOICE
    }
  }

  private[this] def mkPopup(): ListPopup =
    JBPopupFactory.getInstance.createListPopup(new MyPopupStep)

  override def actionPerformed(e: AnActionEvent): Unit = {
//    JBPopupFactory.getInstance.createListPopup(new MyPopupStep).showInBestPositionFor(e.getDataContext)
    e.getInputEvent match {
      case evt: MouseEvent => mkPopup().show(new RelativePoint(evt))
      case _               => mkPopup().show(e.getInputEvent.getComponent)
    }
  }

}
