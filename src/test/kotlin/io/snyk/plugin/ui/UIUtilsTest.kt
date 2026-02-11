package io.snyk.plugin.ui

import com.intellij.ui.components.ActionLink
import org.junit.Test

class UIUtilsTest {

  @Test
  fun `descriptionHeaderPanel should not fail with customLabels`() {
    descriptionHeaderPanel(
      issueNaming = "test naming",
      customLabels = (1..10).toList().map { ActionLink(it.toString()) },
    )
  }
}
