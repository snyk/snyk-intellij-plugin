package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract class DeepCodeParamsBase {

  // Settings
  private boolean isEnable;
  private boolean useLinter;
  private int minSeverity;
  private String sessionToken;

  // Inner params
  private String loginUrl;
  private String ideProductName;
  private String orgDisplayName;
  private Supplier<Long> getTimeoutForGettingAnalysesMs;
  private final DeepCodeRestApi restApi;

  protected DeepCodeParamsBase(
    boolean isEnable,
    String apiUrl,
    boolean disableSslVerification,
    boolean useLinter,
    int minSeverity,
    String sessionToken,
    String loginUrl,
    String ideProductName,
    String orgDisplayName,
    Supplier<Long> getTimeoutForGettingAnalysesMs,
    DeepCodeRestApi restApi
  ) {
    this.isEnable = isEnable;
    this.useLinter = useLinter;
    this.minSeverity = minSeverity;
    this.sessionToken = sessionToken;
    this.loginUrl = loginUrl;
    this.ideProductName = ideProductName;
    this.orgDisplayName = orgDisplayName;
    this.getTimeoutForGettingAnalysesMs = getTimeoutForGettingAnalysesMs;
    this.restApi = restApi;
  }

  public void clearLoginParams() {
    setSessionToken("");
    setLoginUrl("");
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public String getLoginUrl() {
    return loginUrl;
  }

  public void setLoginUrl(String loginUrl) {
    this.loginUrl = loginUrl;
  }

  public boolean useLinter() {
    return useLinter;
  }

  public void setUseLinter(boolean useLinter) {
    this.useLinter = useLinter;
  }

  public int getMinSeverity() {
    return minSeverity;
  }

  public void setMinSeverity(int minSeverity) {
    this.minSeverity = minSeverity;
  }

  public boolean isEnable() {
    return isEnable;
  }

  public void setEnable(boolean isEnable) {
    this.isEnable = isEnable;
  }

  public abstract boolean consentGiven(@NotNull Object project);

  public abstract void setConsentGiven(@NotNull Object project);

  public String getIdeProductName() {
    return ideProductName;
  }

  public long getTimeoutForGettingAnalysesMs() {
    return getTimeoutForGettingAnalysesMs.get();
  }

  public String getOrgDisplayName() {
    return orgDisplayName;
  }
}
