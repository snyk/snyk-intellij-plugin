package snyk.analytics;

import ly.iterative.itly.Logger;
import org.jetbrains.annotations.NotNull;

public class ItlyLogger implements Logger {
  private final com.intellij.openapi.diagnostic.Logger log;

  public ItlyLogger(com.intellij.openapi.diagnostic.Logger log) {
    this.log = log;
  }


  @Override
  public void debug(@NotNull String s) {
    log.debug(s);
  }

  @Override
  public void error(@NotNull String s) {
    log.error(s);
  }

  @Override
  public void info(@NotNull String s) {
    log.info(s);
  }

  @Override
  public void warn(@NotNull String s) {
    log.warn(s);
  }
}
