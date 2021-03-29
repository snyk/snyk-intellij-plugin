package io.snyk.plugin.ui.toolwindow;

import javax.swing.*;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.snyk.plugin.events.SnykCliDownloadListener;
import io.snyk.plugin.services.SnykAnalyticsService;
import io.snyk.plugin.services.SnykCliAuthenticationService;
import io.snyk.plugin.snykcode.core.SnykCodeParams;
import org.jetbrains.annotations.NotNull;

import static io.snyk.plugin.UtilsKt.getApplicationSettingsStateService;

public class SnykAuthPanel {
  private JPanel rootPanel;
  private JButton connectIntelliJToSnykButton;

  public SnykAuthPanel(@NotNull Project project) {
    connectIntelliJToSnykButton.addActionListener(e -> {
      project.getService(SnykToolWindowPanel.class).cleanUiAndCaches();
      final String token = ApplicationManager.getApplication().getService(SnykCliAuthenticationService.class).authenticate();
      getApplicationSettingsStateService().setToken(token);
      SnykCodeParams.Companion.getInstance().setSessionToken(token);

      SnykAnalyticsService analytics = ApplicationManager.getApplication().getService(SnykAnalyticsService.class);
      String userId = analytics.obtainUserId(token);
      analytics.alias(userId);

      project.getMessageBus().syncPublisher(SnykCliDownloadListener.Companion.getCLI_DOWNLOAD_TOPIC())
        .checkCliExistsFinished();
    });
  }

  public JPanel getRoot() {
    return rootPanel;
  }

}
