package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm._
import com.intellij.ui.content._

import javax.swing._
import java.awt.event._
import java.util.Calendar

class MyForm extends ToolWindowFactory {
    private var refreshToolWindowButton: JButton = null
    private var hideToolWindowButton: JButton = null
    private var currentDate: JLabel = null
    private var currentTime: JLabel = null
    private var timeZone: JLabel = null
    private var myToolWindowContent: JPanel = null
    private var myToolWindow: ToolWindow = null

    hideToolWindowButton.addActionListener { _ => myToolWindow.hide(null) }
    refreshToolWindowButton.addActionListener { _ => currentDateTime() }

    // Create the tool window content.
    override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
        myToolWindow = toolWindow
        this.currentDateTime()
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(myToolWindowContent, "", false)
        toolWindow.getContentManager.addContent(content)

    }

    def currentDateTime() {
        // Get current date and time
        val instance = Calendar.getInstance()
        currentDate.setText (
            instance.get(Calendar.DAY_OF_MONTH).toString() + "/"
                + (instance.get(Calendar.MONTH) + 1).toString() + "/" +
                instance.get(Calendar.YEAR).toString()
        )
        currentDate.setIcon(Icons.calendar)
        val min = instance.get(Calendar.MINUTE)
        val strMin: String = if (min < 10) "0" + min.toString else min.toString
        currentTime.setText(instance.get(Calendar.HOUR_OF_DAY).toString() + ":" + strMin)
        currentTime.setIcon(Icons.time)
        // Get time zone
        val gmt_Offset = instance.get(Calendar.ZONE_OFFSET).toLong // offset from GMT in milliseconds
        var str_gmt_Offset = (gmt_Offset / 3600000).toString
        str_gmt_Offset = if (gmt_Offset > 0) "GMT + $str_gmt_Offset" else "GMT - $str_gmt_Offset"
        timeZone.setText(str_gmt_Offset)
        timeZone.setIcon(Icons.timezone)
    }

}
