package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.core.mocks.AnalysisDataBaseMock;
import ai.deepcode.javaclient.core.mocks.DeepCodeIgnoreInfoHolderMock;
import ai.deepcode.javaclient.core.mocks.DeepCodeParamsMock;
import ai.deepcode.javaclient.core.mocks.DeepCodeRestApiMock;
import ai.deepcode.javaclient.core.mocks.DeepCodeUtilsMock;
import ai.deepcode.javaclient.core.mocks.HashContentUtilsMock;
import ai.deepcode.javaclient.core.mocks.LoggerMock;
import ai.deepcode.javaclient.core.mocks.PlatformDependentUtilsMock;
import ai.deepcode.javaclient.core.mocks.RunUtilsBaseMock;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertFalse;

public class RunUtilsTest {

  @Test
  public void isRescanShownAsNotRequestedAfterScanFinishedTest() {

    final boolean[] isFullRescanRequested = {true};

    // ---------------------------- setup --------------------------
    final PlatformDependentUtilsBase pdUtils = new PlatformDependentUtilsMock() {
    };
    final DCLoggerBase dcLogger = new LoggerMock();
    final DeepCodeRestApi restApi = new DeepCodeRestApiMock();

    final DeepCodeParamsBase deepCodeParams = new DeepCodeParamsMock(restApi);

    final HashContentUtilsBase hashContentUtils = new HashContentUtilsMock(pdUtils);

    final DeepCodeIgnoreInfoHolderBase ignoreInfoHolder =
      new DeepCodeIgnoreInfoHolderMock(hashContentUtils, pdUtils, dcLogger);

    final AnalysisDataBase analysisData =
      new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi) {
        @Override
        public void updateCachedResultsForFiles(
          @NotNull Object project,
          @NotNull Collection<Object> allProjectFiles,
          @NotNull Object progress,
          @NotNull String requestId) {
        }
      };

    final DeepCodeUtilsBase deepCodeUtils =
      new DeepCodeUtilsMock(analysisData, deepCodeParams, ignoreInfoHolder, pdUtils, dcLogger, restApi);

    final String project = "Project";

    RunUtilsBase runUtilsMock =
      new RunUtilsBaseMock(pdUtils, hashContentUtils, analysisData, deepCodeUtils, dcLogger) {
        @Override
        protected void updateAnalysisResultsUIPresentation(
          @NotNull Object project, @NotNull Collection<Object> files) {
          isFullRescanRequested[0] = isFullRescanRequested(project);
        }
      };

    // --------------------------- actual test --------------------
    runUtilsMock.rescanInBackgroundCancellableDelayed(project, 0, false, false);

    assertFalse(
      "isFullRescanRequested() should be False after rescan finished and before UI updates",
      isFullRescanRequested[0]);
  }
}
