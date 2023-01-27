package ai.deepcode.javaclient.responses;

import java.util.List;

public class Suggestion {

  private final String id;
  private final String rule;
  private final String message;
  private final String title;
  private final String text;
  private final int severity;
  private final int repoDatasetSize;
  private final List<String> exampleCommitDescriptions;
  private final List<ExampleCommitFix> exampleCommitFixes;
  private final List<String> categories;
  private final List<String> tags;
  private final List<String> cwe;
  private final String leadURL;

  public Suggestion(
    String id,
    String rule,
    String message,
    String title,
    String text,
    int severity,
    int repoDatasetSize,
    List<String> exampleCommitDescriptions,
    List<ExampleCommitFix> exampleCommitFixes,
    List<String> categories,
    List<String> tags,
    List<String> cwe,
    String leadURL) {
    super();
    this.id = id;
    this.rule = rule;
    this.message = message;
    this.title = title;
    this.text = text;
    this.severity = severity;
    this.repoDatasetSize = repoDatasetSize;
    this.exampleCommitDescriptions = exampleCommitDescriptions;
    this.exampleCommitFixes = exampleCommitFixes;
    this.categories = categories;
    this.tags = tags;
    this.cwe = cwe;
    this.leadURL = leadURL;
  }

  public String getId() {
    return id;
  }

  public String getMessage() {
    return message;
  }

  public int getSeverity() {
    return severity;
  }

  @Override
  public String toString() {
    return " id: "
      + id
      + " rule: "
      + rule
      + " message: "
      + message
      + " title: "
      + title
      + " text: "
      + text
      + " severity: "
      + severity
      + " repoDatasetSize: "
      + repoDatasetSize
      + " exampleCommitFixes.size: "
      + exampleCommitFixes.size()
      + " exampleCommitDescriptions: "
      + exampleCommitDescriptions
      + " categories: "
      + categories
      + " tags: "
      + tags
      + " cwe: "
      + cwe
      + "\n";
  }

  public String getRule() {
    return rule;
  }

  public int getRepoDatasetSize() {
    return repoDatasetSize;
  }

  public List<ExampleCommitFix> getExampleCommitFixes() {
    return exampleCommitFixes;
  }

  public String getTitle() {
    return title;
  }

  public String getText() {
    return text;
  }

  public List<String> getExampleCommitDescriptions() {
    return exampleCommitDescriptions;
  }

  public List<String> getCategories() {
    return categories;
  }

  public List<String> getTags() {
    return tags;
  }

  public List<String> getCwe() {
    return cwe;
  }

  public String getLeadURL() {
    return leadURL;
  }
}
