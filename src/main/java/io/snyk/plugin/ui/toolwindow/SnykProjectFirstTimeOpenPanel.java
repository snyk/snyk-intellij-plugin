package io.snyk.plugin.ui.toolwindow;

import com.intellij.openapi.project.Project;
import io.snyk.plugin.events.SnykCliDownloadListener;
import io.snyk.plugin.services.SnykTaskQueueService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SnykProjectFirstTimeOpenPanel {
  private JPanel rootPanel;
  private JButton analyzeNowButton;
  private JCheckBox cliCheckBox;
  private JLabel cliLabel;
  private JCheckBox snykCodeCheckBox;
  private JLabel snykCodeLabel;

  public SnykProjectFirstTimeOpenPanel(@NotNull Project project) {
    analyzeNowButton.addActionListener(e -> {
      // todo
      project.getMessageBus().syncPublisher(SnykCliDownloadListener.Companion.getCLI_DOWNLOAD_TOPIC())
        .firstTimeProjectOpenSetupFinished();
      project.getService(SnykTaskQueueService.class).scan();
    });
  }

  public JPanel getRoot() {
    return rootPanel;
  }

}
