package ai.deepcode.javaclient.responses;

import java.util.List;

public class ExampleCommitFix {
  private String commitURL;
  private List<ExampleLine> lines;

  public ExampleCommitFix(String commitURL, List<ExampleLine> lines) {
    this.commitURL = commitURL;
    this.lines = lines;
  }

  public String getCommitURL() {
    return commitURL;
  }

  public List<ExampleLine> getLines() {
    return lines;
  }
}
