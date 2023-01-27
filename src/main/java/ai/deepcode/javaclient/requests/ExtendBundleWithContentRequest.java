package ai.deepcode.javaclient.requests;

import java.util.List;

public class ExtendBundleWithContentRequest {
  private final FileContentRequest files;
  private final List<String> removedFiles;

  /**
   * @param files        FileContentRequest
   * @param removedFiles List of FilePaths
   */
  public ExtendBundleWithContentRequest(FileContentRequest files, List<String> removedFiles) {
    super();
    this.files = files;
    this.removedFiles = removedFiles;
  }

  public FileContentRequest getFiles() {
    return files;
  }

  public List<String> getRemovedFiles() {
    return removedFiles;
  }
}
