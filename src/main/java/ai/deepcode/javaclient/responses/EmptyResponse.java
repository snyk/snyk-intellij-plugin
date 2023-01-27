package ai.deepcode.javaclient.responses;

public class EmptyResponse {
  private int statusCode = 0;
  private String statusDescription = "";

  public EmptyResponse() {
    this.statusDescription =
      "Error connecting to the server. Check your Settings, Network connection and/or try again later.";
  }

  public void setStatusCode(int statusCode) {
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getStatusDescription() {
    return statusDescription;
  }

  public void setStatusDescription(String statusDescription) {
    this.statusDescription = statusDescription;
  }
}
