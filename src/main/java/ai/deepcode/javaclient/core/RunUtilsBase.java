package ai.deepcode.javaclient.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class RunUtilsBase {

  protected final PlatformDependentUtilsBase pdUtils;
  private final HashContentUtilsBase hashContentUtils;
  private final AnalysisDataBase analysisData;
  private final DeepCodeUtilsBase deepCodeUtils;
  protected final DCLoggerBase dcLogger;

  protected RunUtilsBase(
    PlatformDependentUtilsBase pdUtils,
    HashContentUtilsBase hashContentUtils,
    AnalysisDataBase analysisData,
    DeepCodeUtilsBase deepCodeUtils,
    DCLoggerBase dcLogger) {
    this.pdUtils = pdUtils;
    this.hashContentUtils = hashContentUtils;
    this.analysisData = analysisData;
    this.deepCodeUtils = deepCodeUtils;
    this.dcLogger = dcLogger;
  }

  /**
   * Should NOT run another Background task with Progress re-usage from inside as Progress will be
   * removed from mapProject2Progresses after the end of this task.
   *
   * <p>Will try to re-use parent Progress if possible
   */
  public void runInBackground(
    @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    dcLogger.logInfo("runInBackground requested");
    final Consumer<Object> wrappedConsumer =
      (progress) -> {
        dcLogger.logInfo(
          "New Process ["
            + progress.toString()
            + "] \nstarted at "
            + pdUtils.getProjectName(project));
        getRunningProgresses(project).add(progress);

        progressConsumer.accept(progress);

        dcLogger.logInfo(
          "Process ["
            + progress.toString()
            + "] \nending at "
            + pdUtils.getProjectName(project));
        getRunningProgresses(project).remove(progress);
      };
    if (!reuseCurrentProgress(project, title, wrappedConsumer)) {
      doBackgroundRun(project, title, wrappedConsumer);
    }
    ;
  }

  /**
   * Should implement reuse of currently running parent DeepCode Progress if possible
   *
   * @return true if reuse been successful
   */
  protected abstract boolean reuseCurrentProgress(
    @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer);

  /**
   * Should implement background task creation with call of progressConsumer() inside Job.run()
   */
  protected abstract void doBackgroundRun(
    @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer);
  // indicator.setIndeterminate(false);
  // "DeepCode: " + title

  // protected abstract String getProgressPresentation(@NotNull Object progress);
  // progress.toString()

  private static final Map<Object, Set<Object>> mapProject2Progresses = new ConcurrentHashMap<>();

  protected static synchronized Set<Object> getRunningProgresses(@NotNull Object project) {
    return mapProject2Progresses.computeIfAbsent(project, p -> ConcurrentHashMap.newKeySet());
  }

  // ??? list of all running background tasks
  // com.intellij.openapi.wm.ex.StatusBarEx#getBackgroundProcesses
  // todo? Disposer.register(project, this)
  // https://intellij-support.jetbrains.com/hc/en-us/community/posts/360008241759/comments/360001689399
  public void cancelRunningIndicators(@NotNull Object project) {
    String indicatorsList =
      getRunningProgresses(project).stream()
        .map(Object::toString)
        .collect(Collectors.joining("\n"));
    dcLogger.logInfo("Canceling ProgressIndicators:\n" + indicatorsList);
    bulkModeForceUnset(project);
    getRunningProgresses(project).forEach(this::cancelProgress);
    getRunningProgresses(project).clear();
    projectsWithFullRescanRequested.remove(project);
  }

  protected abstract void cancelProgress(@NotNull Object progress);
  // ProgressIndicator.cancel()

  protected abstract void bulkModeForceUnset(@NotNull Object project);
  // in case any indicator holds Bulk mode process
  // BulkMode.forceUnset(project);

  private static final Map<Object, Object> mapFileProcessed2CancellableProgress =
    new ConcurrentHashMap<>();

  private static final Map<Object, Consumer<Object>> mapFile2Runnable = new ConcurrentHashMap<>();

  /**
   * Could run another Background task from inside as Progress is NOT removed from
   * mapProject2Progresses after the end of this task.
   *
   * <p>Will NOT re-use parent Progress.
   */
  public void runInBackgroundCancellable(
    @NotNull Object file, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    final String s = progressConsumer.toString();
    final int indexOfSlash = s.lastIndexOf('/');
    final String runId = s.substring(Math.max(indexOfSlash, 0), s.length() - 1);
    dcLogger.logInfo(
      "runInBackgroundCancellable requested for: "
        + pdUtils.getFileName(file)
        + " with progressConsumer "
        + runId);
    // can't use `file` as Id cause in Idea same file may have different PsiFile instances during
    // lifetime
    final String fileId = pdUtils.getDeepCodedFilePath(file);

    // To proceed multiple PSI events in a bunch (every 100 milliseconds)
    Consumer<Object> prevRunnable = mapFile2Runnable.put(fileId, progressConsumer);
    if (prevRunnable != null) return;
    dcLogger.logInfo(
      "new Background task registered for: "
        + pdUtils.getFileName(file)
        + " with progressConsumer "
        + runId);

    final Object project = pdUtils.getProject(file);
    // analysisData.setUpdateInProgress(project);

    doBackgroundRun(
      project,
      title,
      (progress) -> {
        // To let new event cancel the currently running one
        Object prevProgress = mapFileProcessed2CancellableProgress.put(fileId, progress);
        if (prevProgress != null
          // can't use prevProgressIndicator.isRunning() due to
          // https://youtrack.jetbrains.com/issue/IDEA-241055
          && getRunningProgresses(project).contains(prevProgress)) {
          dcLogger.logInfo(
            "Previous Process cancelling for "
              + pdUtils.getFileName(file)
              + "\nProgress ["
              + prevProgress.toString()
              + "]");
          cancelProgress(prevProgress);
          getRunningProgresses(project).remove(prevProgress);
          hashContentUtils.removeFileHashContent(file);
        }
        getRunningProgresses(project).add(progress);

        // small delay to let new consequent requests proceed and cancel current one
        pdUtils.delay(pdUtils.DEFAULT_DELAY_SMALL, progress);

        Consumer<Object> actualRunnable = mapFile2Runnable.get(fileId);
        if (actualRunnable != null) {
          final String s1 = actualRunnable.toString();
          final int indexOfSlash1 = s1.lastIndexOf('/');
          final String runIdToRun = s1.substring(Math.max(indexOfSlash1, 0), s1.length() - 1);
          dcLogger.logInfo(
            "New Process started for "
              + pdUtils.getFileName(file)
              + " with Runnable "
              + runIdToRun);
          mapFile2Runnable.remove(fileId);

          // final delay before actual heavy Network request
          // to let new consequent requests proceed and cancel current one
          pdUtils.delay(pdUtils.DEFAULT_DELAY, progress);
          actualRunnable.accept(progress);

        } else {
          dcLogger.logWarn("No actual Runnable found for: " + pdUtils.getFileName(file));
        }
        dcLogger.logInfo("Process ending for " + pdUtils.getFileName(file));
      });
  }

  public boolean isFullRescanRequested(@NotNull Object project) {
    return projectsWithFullRescanRequested.contains(project);
  }

  private static final Set<Object> projectsWithFullRescanRequested = ConcurrentHashMap.newKeySet();

  private static final Map<Object, Object> mapProject2CancellableIndicator =
    new ConcurrentHashMap<>();
  private static final Map<Object, Long> mapProject2CancellableRequestId =
    new ConcurrentHashMap<>();

  private static final Map<Object, Long> mapProject2RequestId = new ConcurrentHashMap<>();
  private static final Set<Long> bulkModeRequests = ConcurrentHashMap.newKeySet();

  protected abstract void bulkModeUnset(@NotNull Object project);
  // BulkMode.unset(project);

  public void rescanInBackgroundCancellableDelayed(
    @NotNull Object project, int delayMilliseconds, boolean inBulkMode) {
    rescanInBackgroundCancellableDelayed(project, delayMilliseconds, inBulkMode, true);
  }

  public void rescanInBackgroundCancellableDelayed(
    @NotNull Object project,
    int delayMilliseconds,
    boolean inBulkMode,
    boolean invalidateCaches) {
    final long requestId = System.currentTimeMillis();
    dcLogger.logInfo(
      "rescanInBackgroundCancellableDelayed requested for: ["
        + pdUtils.getProjectName(project)
        + "] with RequestId "
        + requestId);
    projectsWithFullRescanRequested.add(project);

    // To proceed multiple events in a bunch (every <delayMilliseconds>)
    Long prevRequestId = mapProject2RequestId.put(project, requestId);
    if (inBulkMode) bulkModeRequests.add(requestId);
    if (prevRequestId != null) {
      if (bulkModeRequests.remove(prevRequestId)) {
        bulkModeUnset(project);
      }
      return;
    }
    dcLogger.logInfo(
      "new Background Rescan task registered for ["
        + pdUtils.getProjectName(project)
        + "] with RequestId "
        + requestId);

    doBackgroundRun(
      project,
      "Full Project re-Analysing for " + pdUtils.getProjectName(project),
      (progress) -> {
        // To let new event cancel the currently running one
        Object prevProgressIndicator = mapProject2CancellableIndicator.put(project, progress);
        if (prevProgressIndicator != null
          // can't use prevProgressIndicator.isRunning() due to
          // https://youtrack.jetbrains.com/issue/IDEA-241055
          && getRunningProgresses(project).remove(prevProgressIndicator)) {
          dcLogger.logInfo(
            "Previous Rescan cancelling for "
              + pdUtils.getProjectName(project)
              + "\nProgress ["
              + prevProgressIndicator.toString()
              + "]");
          cancelProgress(prevProgressIndicator);
        }
        getRunningProgresses(project).add(progress);

        // unset BulkMode if cancelled process did run under BulkMode
        Long prevReqId = mapProject2CancellableRequestId.put(project, requestId);
        if (prevReqId != null && bulkModeRequests.remove(prevReqId)) {
          bulkModeUnset(project);
        }

        try {
          // delay to let new consequent requests proceed and cancel current one
          // or to let Idea proceed internal events (.gitignore update)
          pdUtils.delay(delayMilliseconds, progress);

          Long actualRequestId = mapProject2RequestId.get(project);
          if (actualRequestId != null) {
            dcLogger.logInfo(
              "New Rescan started for ["
                + pdUtils.getProjectName(project)
                + "] with RequestId "
                + actualRequestId);
            mapProject2RequestId.remove(project);

            // actual rescan
            if (invalidateCaches) analysisData.removeProjectFromCaches(project);
            updateCachedAnalysisResults(project, progress);

            if (bulkModeRequests.remove(actualRequestId)) {
              bulkModeUnset(project);
            }
          } else {
            dcLogger.logWarn("No actual RequestId found for: " + pdUtils.getProjectName(project));
          }
          dcLogger.logInfo("Rescan ending for " + pdUtils.getProjectName(project));
        } finally {
          projectsWithFullRescanRequested.remove(project);
        }
      });
  }

  public void asyncAnalyseProjectAndUpdatePanel(@Nullable Object project) {
    final Object[] projects =
      (project == null) ? pdUtils.getOpenProjects() : new Object[]{project};
    for (Object prj : projects) {
      if (!isFullRescanRequested(prj)) {
        rescanInBackgroundCancellableDelayed(
          prj, PlatformDependentUtilsBase.DEFAULT_DELAY_SMALL, false);
      }
    }
  }

  public void updateCachedAnalysisResults(@NotNull Object project, @NotNull Object progress) {
    try {
      final List<Object> allSupportedFilesInProject =
        deepCodeUtils.getAllSupportedFilesInProject(project, true, progress);

      analysisData.updateCachedResultsForFiles(project, allSupportedFilesInProject, progress);

    } finally {
      projectsWithFullRescanRequested.remove(project);
      updateAnalysisResultsUIPresentation(
        project, analysisData.getAllFilesWithSuggestions(project));
    }
  }

  protected abstract void updateAnalysisResultsUIPresentation(
    @NotNull Object project, @NotNull Collection<Object> files);
}
