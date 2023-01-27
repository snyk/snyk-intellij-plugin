package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class PlatformDependentUtilsMock extends PlatformDependentUtilsBase {

  @Override
  public Object getProject(Object file) {
    return null;
  }

  @Override
  public String getProjectName(Object project) {
    return null;
  }

  @Override
  public String getFileName(Object file) {
    return null;
  }

  @Override
  public String getFilePath(Object file) {
    return null;
  }

  @Override
  public String getDirPath(Object file) {
    return null;
  }

  @Override
  protected String getProjectBasedFilePath(Object file) {
    return null;
  }

  @Override
  public Object getFileByDeepcodedPath(String path, Object project) {
    return null;
  }

  @Override
  public Object[] getOpenProjects() {
    return new Object[0];
  }

  @Override
  public long getFileSize(Object file) {
    return 0;
  }

  @Override
  public int getLineStartOffset(Object file, int line) {
    return 0;
  }

  @Override
  public void runInBackgroundCancellable(
    Object file, String title, Consumer<Object> progressConsumer) {
  }

  @Override
  public void runInBackground(
    Object project, String title, Consumer<Object> progressConsumer) {
  }

  @Override
  public void cancelRunningIndicators(Object project) {
  }

  @Override
  public void doFullRescan(Object project) {
  }

  @Override
  public void refreshPanel(Object project) {
  }

  @Override
  public boolean isLogged(@Nullable Object project, boolean userActionNeeded) {
    return false;
  }

  @Override
  public void progressSetText(@Nullable Object progress, String text) {
  }

  @Override
  public void progressCheckCanceled(@Nullable Object progress) {
  }

  @Override
  public boolean progressCanceled(@Nullable Object progress) {
    return false;
  }

  @Override
  public void progressSetFraction(@Nullable Object progress, double fraction) {
  }

  @Override
  public void showInBrowser(String url) {
  }

  @Override
  public void showLoginLink(@Nullable Object project, String message) {
  }

  @Override
  public void showConsentRequest(Object project, boolean userActionNeeded) {
  }

  @Override
  public void showInfo(String message, @Nullable Object project) {
  }

  @Override
  public void showWarn(String message, @Nullable Object project, boolean wasWarnShown) {
    System.out.println("WARN " + (wasWarnShown ? "" : "!!! shown to user !!! ") + message);
  }

  @Override
  public void showError(String message, @Nullable Object project) {
  }
}
