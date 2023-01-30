package ai.deepcode.javaclient.requests;

import java.util.List;

public class ExtendBundleWithHashRequest {
  private final FileHashRequest files;
  private final List<String> removedFiles;

  /**
   * @param fileHashRequest FileHashRequest i.e. filePath: fileHash
   * @param removedFiles    List of FilePaths
   */
  public ExtendBundleWithHashRequest(FileHashRequest fileHashRequest, List<String> removedFiles) {
    this.files = fileHashRequest;
    this.removedFiles = removedFiles;
  }

  public FileHashRequest getFiles() {
    return files;
  }

  public List<String> getRemovedFiles() {
    return removedFiles;
  }
}
