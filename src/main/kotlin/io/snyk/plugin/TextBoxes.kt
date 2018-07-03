package io.snyk.plugin

import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.Messages


// If you register the action from Java code, this constructor is used to set the menu item name
// (optionally, you can specify the menu description and an icon to display next to the menu item).
// You can omit this constructor when registering the action in the plugin.xml file.
class TextBoxes : AnAction("Text _Boxes") {

    // Set the menu item name, description and icon.
// super("Text _Boxes","Item description",IconLoader.getIcon("/Mypackage/icon.png"));

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.getData(PlatformDataKeys.PROJECT)

        val txt = Messages.showInputDialog(
            project,
            "What is your name?",
            "Input your name",
            Messages.getQuestionIcon()
        )
        
        Messages.showMessageDialog(
            project,
            "Hello, $txt!\n I am glad to see you.",
            "Information",
            Messages.getInformationIcon()
        )
    }
}
