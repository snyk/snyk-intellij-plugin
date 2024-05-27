package ai.deepcode.javaclient.responses;

public class ExampleLine {
  private String line;
  private int lineNumber;
  private String lineChange;

  public ExampleLine(String line, int lineNumber, String lineChange) {
    this.line = line;
    this.lineNumber = lineNumber;
    this.lineChange = lineChange;
  }

  public String getLine() {
    return line;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public String getLineChange() {
    return lineChange;
  }
}
