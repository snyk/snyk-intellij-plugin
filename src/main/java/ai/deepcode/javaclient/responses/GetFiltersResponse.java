package ai.deepcode.javaclient.responses;

import java.util.Collections;
import java.util.List;

public class GetFiltersResponse extends EmptyResponse {
  private final List<String> extensions;
  private final List<String> configFiles;

  public GetFiltersResponse() {
    super();
    extensions = Collections.emptyList();
    configFiles = Collections.emptyList();
  }

  public GetFiltersResponse(List<String> extensions, List<String> configFiles) {
    this.extensions = extensions;
    this.configFiles = configFiles;
  }

  public List<String> getExtensions() {
    return extensions;
  }

  public List<String> getConfigFiles() {
    return configFiles;
  }
}
