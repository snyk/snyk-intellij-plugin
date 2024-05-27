package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.responses.ExampleCommitFix;

import java.util.List;

public class SuggestionForFile {
  private final String id;
  private final String rule;
  private final String message;
  private final String title;
  private final String text;
  private final int severity;
  private final int repoDatasetSize;
  private final List<String> exampleCommitDescriptions;
  private final List<ExampleCommitFix> exampleCommitFixes;
  private final List<MyTextRange> ranges;
  private final List<String> categories;
  private final List<String> tags;
  private final List<String> cwe;

  public SuggestionForFile(
    String id,
    String rule,
    String message,
    String title,
    String text,
    int severity,
    int repoDatasetSize,
    List<String> exampleCommitDescriptions,
    List<ExampleCommitFix> exampleCommitFixes,
    List<MyTextRange> ranges,
    List<String> categories,
    List<String> tags,
    List<String> cwe) {
    this.id = id;
    this.rule = rule;
    this.message = message;
    this.title = title;
    this.text = text;
    this.severity = severity;
    this.repoDatasetSize = repoDatasetSize;
    this.exampleCommitDescriptions = exampleCommitDescriptions;
    this.exampleCommitFixes = exampleCommitFixes;
    this.ranges = ranges;
    this.categories = categories;
    this.tags = tags;
    this.cwe = cwe;
  }

  public String getId() {
    return id;
  }

  public String getRule() {
    return rule;
  }

  public String getMessage() {
    return message;
  }

  public List<MyTextRange> getRanges() {
    return ranges;
  }

  public int getSeverity() {
    return severity;
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

  public List<String> getCwe() {
    return cwe;
  }

  public List<String> getCategories() {
    return categories;
  }

  public String getText() {
    return text;
  }

  public List<String> getExampleCommitDescriptions() {
    return exampleCommitDescriptions;
  }

  public List<String> getTags() {
    return tags;
  }
}
