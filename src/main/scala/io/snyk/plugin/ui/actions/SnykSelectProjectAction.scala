package io.snyk.plugin.ui.actions

import java.awt.event.MouseEvent
import java.util

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.popup._
import com.intellij.ui.awt.RelativePoint
import icons.MavenIcons
import io.snyk.plugin.ui.state.SnykPluginState
import javax.swing.Icon

import scala.collection.JavaConverters._

class SnykSelectProjectAction() extends AnAction(MavenIcons.MavenProject) {

  class MyPopupStep(pluginState: SnykPluginState) extends ListPopupStep[String] {
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
        pluginState.navigator().navToVulns()
      }
      PopupStep.FINAL_CHOICE
    }
  }

  private[this] def mkPopup(pluginState: SnykPluginState): ListPopup =
    JBPopupFactory.getInstance.createListPopup(new MyPopupStep(pluginState))

  override def actionPerformed(event: AnActionEvent): Unit = {
    def pluginState = SnykPluginState.getInstance(event.getProject)

    event.getInputEvent match {
      case evt: MouseEvent => mkPopup(pluginState).show(new RelativePoint(evt))
      case _               => mkPopup(pluginState).show(event.getInputEvent.getComponent)
    }
  }

}
