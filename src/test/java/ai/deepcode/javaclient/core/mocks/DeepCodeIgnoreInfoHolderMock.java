package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.core.DCLoggerBase;
import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase;
import ai.deepcode.javaclient.core.HashContentUtilsBase;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import org.jetbrains.annotations.NotNull;

public class DeepCodeIgnoreInfoHolderMock extends DeepCodeIgnoreInfoHolderBase {

  public DeepCodeIgnoreInfoHolderMock(
    @NotNull HashContentUtilsBase hashContentUtils,
    @NotNull PlatformDependentUtilsBase pdUtils,
    @NotNull DCLoggerBase dcLogger) {
    super(hashContentUtils, pdUtils, dcLogger);
  }
}
