package ai.deepcode.javaclient.responses;

public class GetAnalysisResponse extends EmptyResponse {
  private final String status;
  private final double progress;
  private final String analysisURL;
  private final FilesMap files;
  private final Suggestions suggestions;

  public GetAnalysisResponse() {
    super();
    status = "";
    progress = 0;
    analysisURL = "";
    suggestions = null;
    files = null;
  }

  public GetAnalysisResponse(
    String status,
    double progress,
    String analysisURL,
    FilesMap analysisResults,
    Suggestions suggestions) {
    this.status = status;
    this.progress = progress;
    this.analysisURL = analysisURL;
    this.files = analysisResults;
    this.suggestions = suggestions;
  }

  public String getStatus() {
    return status;
  }

  public double getProgress() {
    return progress;
  }

  public String getAnalysisURL() {
    return analysisURL;
  }

  public FilesMap getFiles() {
    return files;
  }

  @Override
  public String toString() {
    return "GetAnalysisResponse object:"
      + "\nstatus: "
      + status
      + "\nprogress: "
      + progress
      + "\nanalysisURL: "
      + analysisURL
      + "\nanalysisResult: "
      + suggestions;
  }

  public Suggestions getSuggestions() {
    return suggestions;
  }
}
