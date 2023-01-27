package ai.deepcode.javaclient.responses;

public class LoginResponse extends EmptyResponse {
  private final String sessionToken;
  private final String loginURL;

  public LoginResponse() {
    super();
    this.sessionToken = "";
    this.loginURL = "";
  }

  private LoginResponse(String sessionToken, String loginURL) {
    this.sessionToken = sessionToken;
    this.loginURL = loginURL;
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public String getLoginURL() {
    return loginURL;
  }
}
