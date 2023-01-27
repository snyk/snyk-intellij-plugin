package ai.deepcode.javaclient.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.function.Consumer;

public abstract class PlatformDependentUtilsBase {

  public static final int DEFAULT_DELAY = 1000; // milliseconds
  public static final int DEFAULT_DELAY_SMALL = 200; // milliseconds

  public void delay(int millis, @Nullable Object progress) {
    int counts = millis / 100;
    for (int i = 1; i <= counts; i++) {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      progressCheckCanceled(progress);
    }
  }

  @NotNull
  public abstract Object getProject(@NotNull Object file);

  @NotNull
  public abstract String getProjectName(@NotNull Object project);

  @NotNull
  public abstract String getFileName(@NotNull Object file);

  /**
   * @return path as String with `/` as separator (even on Windows). See {@link File#separatorChar}
   */
  @NotNull
  public abstract String getFilePath(@NotNull Object file);

  /**
   * @return path as String with `/` as separator (even on Windows). See {@link File#separatorChar}
   */
  @NotNull
  public abstract String getDirPath(@NotNull Object file);

  @NotNull
  public String getDeepCodedFilePath(@NotNull Object file) {
    final String path = getProjectBasedFilePath(file);
    return path.startsWith("/") ? path : "/" + path;
  }

  @NotNull
  protected abstract String getProjectBasedFilePath(@NotNull Object file);

  @Nullable
  public abstract Object getFileByDeepcodedPath(String path, Object project);

  public abstract Object[] getOpenProjects();
  // ProjectManager.getInstance().getOpenProjects()

  public abstract long getFileSize(@NotNull Object file);

  public abstract int getLineStartOffset(@NotNull Object file, int line);

  public abstract void runInBackgroundCancellable(
    @NotNull Object file, @NotNull String title, @NotNull Consumer<Object> progressConsumer);

  public abstract void runInBackground(
    @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer);

  public abstract void cancelRunningIndicators(@NotNull Object project);

  public abstract void doFullRescan(@NotNull Object project);

  public abstract void refreshPanel(@NotNull Object project);

  // we can't use LoginUtils direct call due to circular dependencies
  public abstract boolean isLogged(@Nullable Object project, boolean userActionNeeded);

  public abstract void progressSetText(@Nullable Object progress, String text);

  public abstract void progressCheckCanceled(@Nullable Object progress);

  public abstract boolean progressCanceled(@Nullable Object progress);

  public abstract void progressSetFraction(@Nullable Object progress, double fraction);

  public abstract void showInBrowser(@NotNull String url);

  public abstract void showLoginLink(@Nullable Object project, String message);

  public abstract void showConsentRequest(Object project, boolean userActionNeeded);

  public abstract void showInfo(String message, @Nullable Object project);

  public abstract void showWarn(String message, @Nullable Object project, boolean wasWarnShown);

  public abstract void showError(String message, @Nullable Object project);
}
