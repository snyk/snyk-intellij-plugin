package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.core.DCLoggerBase;

public class LoggerMock extends DCLoggerBase {

  public LoggerMock() {
    super(
      () -> msg -> System.out.println("INFO " + msg),
      () -> msg -> System.out.println("WARN " + msg),
      () -> true,
      () -> true,
      "ai.deepcode",
      "");
  }

  @Override
  protected String getExtraInfo() {
    return "";
  }
}
