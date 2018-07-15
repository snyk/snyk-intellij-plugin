package io.snyk.plugin

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.Messages

class TextBoxes extends AnAction("Text _Boxes") {
  override def actionPerformed(event: AnActionEvent): Unit = {
    val project = event.getData(CommonDataKeys.PROJECT)

    val txt = Messages.showInputDialog(
      project,
      "What is your name?",
      "Input your name",
      Messages.getQuestionIcon
    )

    Messages.showMessageDialog(
      project,
      "Hello, $txt!\n I am glad to see you.",
      "Information",
      Messages.getInformationIcon
    )
  }
}
