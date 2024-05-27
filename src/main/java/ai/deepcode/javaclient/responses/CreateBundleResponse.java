package ai.deepcode.javaclient.responses;

import java.util.Collections;
import java.util.List;

public class CreateBundleResponse extends EmptyResponse {
  private final String bundleHash;
  private final List<String> missingFiles;

  public CreateBundleResponse() {
    this("", Collections.emptyList());
  }

  public CreateBundleResponse(String bundleHash, List<String> missingFiles) {
    super();
    this.bundleHash = bundleHash;
    this.missingFiles = missingFiles;
  }

  public String getBundleHash() {
    return bundleHash;
  }

  public List<String> getMissingFiles() {
    return missingFiles;
  }

  @Override
  public String toString() {
    return "CreateBundleResponse{"
      + "bundleHash='"
      + bundleHash
      + '\''
      + ", missingFiles="
      + missingFiles
      + '}';
  }
}
