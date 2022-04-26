package io.snyk.plugin.ui

import org.junit.Test
import javax.swing.JLabel

class UIUtilsTest {

    @Test
    fun `descriptionHeaderPanel should not fail with customLabels`() {
        descriptionHeaderPanel(
            issueNaming = "test naming",
            customLabels = (1..10).toList().map { JLabel(it.toString()) }
        )
    }
}
