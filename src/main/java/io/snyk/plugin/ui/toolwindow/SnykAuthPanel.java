package io.snyk.plugin.ui.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import io.snyk.plugin.events.SnykCliDownloadListener;
import io.snyk.plugin.services.SnykCliAuthenticationService;
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static io.snyk.plugin.UtilsKt.getApplicationSettingsStateService;

public class SnykAuthPanel {
  private JPanel rootPanel;
  private JButton connectIntelliJToSnykButton;

  public SnykAuthPanel(@NotNull Project project) {
    connectIntelliJToSnykButton.addActionListener(e -> {
      // todo
      project.getService(SnykToolWindowPanel.class).cleanAll();
//      ShowSettingsUtil.getInstance().showSettingsDialog(project, SnykProjectSettingsConfigurable.class);
      getApplicationSettingsStateService().setToken(
        ApplicationManager.getApplication().getService(SnykCliAuthenticationService.class).authenticate()
      );
      project.getMessageBus().syncPublisher(SnykCliDownloadListener.Companion.getCLI_DOWNLOAD_TOPIC())
        .checkCliExistsFinished();
    });
  }

  public JPanel getRoot() {
    return rootPanel;
  }

}
