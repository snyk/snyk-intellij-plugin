package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.core.AnalysisDataBase;
import ai.deepcode.javaclient.core.DCLoggerBase;
import ai.deepcode.javaclient.core.DeepCodeUtilsBase;
import ai.deepcode.javaclient.core.HashContentUtilsBase;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import ai.deepcode.javaclient.core.RunUtilsBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Consumer;

public class RunUtilsBaseMock extends RunUtilsBase {

  public RunUtilsBaseMock(
    PlatformDependentUtilsBase pdUtils,
    HashContentUtilsBase hashContentUtils,
    AnalysisDataBase analysisData,
    DeepCodeUtilsBase deepCodeUtils,
    DCLoggerBase dcLogger) {
    super(pdUtils, hashContentUtils, analysisData, deepCodeUtils, dcLogger);
  }

  @Override
  protected boolean reuseCurrentProgress(
    @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    return false;
  }

  @Override
  protected void doBackgroundRun(
    @NotNull Object project, @NotNull String title, @NotNull Consumer<Object> progressConsumer) {
    progressConsumer.accept("progress");
  }

  @Override
  protected void cancelProgress(@NotNull Object progress) {
  }

  @Override
  protected void bulkModeForceUnset(@NotNull Object project) {
  }

  @Override
  protected void bulkModeUnset(@NotNull Object project) {
  }

  @Override
  protected void updateAnalysisResultsUIPresentation(
    @NotNull Object project, @NotNull Collection<Object> files) {
  }
}
