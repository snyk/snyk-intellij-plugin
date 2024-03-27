package snyk

import com.intellij.ui.jcef.JBCefBrowser
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

object UIComponentFinder {
    fun getJButtonByText(parent: Container, text: String): JButton? {
        val components = parent.components
        var found: JButton? = null
        for (component in components) {
            if (component is JButton && text == component.text) {
                found = component
            } else if (component is Container) {
                found = getJButtonByText(component, text)
            }
            if (found != null) {
                break
            }
        }
        return found
    }

    fun getJLabelByText(parent: Container, text: String): JLabel? {
        val components = parent.components
        var found: JLabel? = null
        for (component in components) {
            if (component is JLabel && text == component.text) {
                found = component
            } else if (component is Container) {
                found = getJLabelByText(component, text)
            }
            if (found != null) {
                break
            }
        }
        return found
    }

    fun getJPanelByName(parent: Container, name: String): JPanel? {
        val components = parent.components
        var found: JPanel? = null
        for (component in components) {
            if (component is JPanel && name == component.name) {
                found = component
            } else if (component is Container) {
                found = getJPanelByName(component, name)
            }
            if (found != null) {
                break
            }
        }
        return found
    }

    fun getJBCEFBrowser(parent: Container): JBCefBrowser.MyPanel? {
        val components = parent.components
        var found: JBCefBrowser.MyPanel? = null
        for (component in components) {
            if (component is JBCefBrowser.MyPanel) {
                found = component
            } else if (component is Container) {
                    found = getJBCEFBrowser(component)
            }
            if (found != null) {
                break
            }
        }
        return found
    }
}
