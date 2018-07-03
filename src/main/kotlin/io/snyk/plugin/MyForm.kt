package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.*
import com.intellij.ui.content.*

import javax.swing.*
import java.awt.event.*
import java.util.Calendar

class MyForm : ToolWindowFactory {
    private var refreshToolWindowButton: JButton? = null
    private var hideToolWindowButton: JButton? = null
    private var currentDate: JLabel? = null
    private var currentTime: JLabel? = null
    private var timeZone: JLabel? = null
    private var myToolWindowContent: JPanel? = null
    private var myToolWindow: ToolWindow? = null

    init {
        hideToolWindowButton!!.addActionListener { myToolWindow!!.hide(null) }
        refreshToolWindowButton!!.addActionListener { this@MyForm.currentDateTime() }
    }

    // Create the tool window content.
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        myToolWindow = toolWindow
        this.currentDateTime()
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(myToolWindowContent, "", false)
        toolWindow.contentManager.addContent(content)

    }

    fun currentDateTime() {
        // Get current date and time
        val instance = Calendar.getInstance()
        currentDate!!.text = (instance.get(Calendar.DAY_OF_MONTH).toString() + "/"
                + (instance.get(Calendar.MONTH) + 1).toString() + "/" +
                instance.get(Calendar.YEAR).toString())
        currentDate!!.icon = Icons.calendar
        val min = instance.get(Calendar.MINUTE)
        val strMin: String
        if (min < 10) {
            strMin = "0" + min.toString()
        } else {
            strMin = min.toString()
        }
        currentTime!!.text = instance.get(Calendar.HOUR_OF_DAY).toString() + ":" + strMin
        currentTime!!.icon = Icons.time
        // Get time zone
        val gmt_Offset = instance.get(Calendar.ZONE_OFFSET).toLong() // offset from GMT in milliseconds
        var str_gmt_Offset = (gmt_Offset / 3600000).toString()
        str_gmt_Offset = if (gmt_Offset > 0) "GMT + $str_gmt_Offset" else "GMT - $str_gmt_Offset"
        timeZone!!.text = str_gmt_Offset
        timeZone!!.icon = Icons.timezone
    }

}
