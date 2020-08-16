package io.snyk.plugin.ui

import javax.swing.ImageIcon

class Icons {
    companion object {
        private fun getIconFromResources(name: String): ImageIcon = ImageIcon(this::class.java.getResource(name))

        val VULNERABILITY_24 = getIconFromResources("/icons/vulnerability.png")
        val VULNERABILITY_16 = getIconFromResources("/icons/vulnerability_16.png")
        val HIGH_SEVERITY = getIconFromResources("/icons/high_severity_16.png")
        val LOW_SEVERITY = getIconFromResources("/icons/low_severity_16.png")
        val MEDIUM_SEVERITY = getIconFromResources("/icons/medium_severity_16.png")
    }
}
