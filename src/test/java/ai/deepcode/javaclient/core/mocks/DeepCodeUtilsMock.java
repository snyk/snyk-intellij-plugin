package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.core.AnalysisDataBase;
import ai.deepcode.javaclient.core.DCLoggerBase;
import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase;
import ai.deepcode.javaclient.core.DeepCodeParamsBase;
import ai.deepcode.javaclient.core.DeepCodeUtilsBase;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class DeepCodeUtilsMock extends DeepCodeUtilsBase {

  public DeepCodeUtilsMock(
    @NotNull AnalysisDataBase analysisData,
    @NotNull DeepCodeParamsBase deepCodeParams,
    @NotNull DeepCodeIgnoreInfoHolderBase ignoreInfoHolder,
    @NotNull PlatformDependentUtilsBase pdUtils,
    @NotNull DCLoggerBase dcLogger,
    @NotNull DeepCodeRestApi restApi
  ) {
    super(analysisData, deepCodeParams, ignoreInfoHolder, pdUtils, dcLogger, restApi);
  }

  @Override
  protected Collection<Object> allProjectFiles(@NotNull Object project) {
    return Collections.emptySet();
  }

  @Override
  protected long getFileLength(@NotNull Object file) {
    return 0;
  }

  @Override
  protected String getFileExtention(@NotNull Object file) {
    return "";
  }

  @Override
  protected boolean isGitIgnoredExternalCheck(@NotNull Object file) {
    return false;
  }
}
