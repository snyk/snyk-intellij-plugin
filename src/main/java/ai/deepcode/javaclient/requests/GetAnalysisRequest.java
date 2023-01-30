package ai.deepcode.javaclient.requests;

import java.util.List;
import java.util.Objects;

public class GetAnalysisRequest {
  private GetAnalysisKey key;
  private AnalysisContext analysisContext;
  private Integer severity;
  private boolean prioritized;
  private boolean legacy;

  /**
   * @param bundleHash
   * @param limitToFiles   list of filePath
   * @param severity
   * @param shard          uniq String (hash) per Project to optimize jobs on backend (run on the same worker to reuse caches)
   * @param ideProductName specific IDE
   * @param orgDisplayName clients snyk organization name
   * @param prioritized
   * @param legacy
   */
  public GetAnalysisRequest(
    String bundleHash,
    List<String> limitToFiles,
    Integer severity,
    String shard,
    String ideProductName,
    String orgDisplayName,
    boolean prioritized,
    boolean legacy
  ) {
    this.key = new GetAnalysisKey(bundleHash, limitToFiles, shard);
    this.analysisContext = new AnalysisContext(ideProductName, orgDisplayName);
    this.severity = severity;
    this.prioritized = prioritized;
    this.legacy = legacy;
  }

  public GetAnalysisRequest(
    String bundleHash,
    List<String> limitToFiles,
    Integer severity,
    String shard,
    String ideProductName,
    String orgDisplayName
  ) {
    this(bundleHash, limitToFiles, severity, shard, ideProductName, orgDisplayName, false, true);
  }

  private static class GetAnalysisKey {
    private final String type = "file";
    private final String hash;
    private final List<String> limitToFiles;
    private final String shard;

    public GetAnalysisKey(String hash, List<String> limitToFiles, String shard) {
      this.hash = hash;
      this.limitToFiles = limitToFiles;
      this.shard = shard;
    }

    public String getHash() {
      return hash;
    }

    public List<String> getLimitToFiles() {
      return limitToFiles;
    }

    public String getShard() {
      return shard;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      GetAnalysisKey that = (GetAnalysisKey) o;
      return type.equals(that.type)
        && hash.equals(that.hash)
        && Objects.equals(limitToFiles, that.limitToFiles);
    }

    @Override
    public String toString() {
      return "GetAnalysisKey{"
        + "type='"
        + type
        + '\''
        + ", hash='"
        + hash
        + '\''
        + ", limitToFiles="
        + limitToFiles
        + '}';
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, hash, limitToFiles);
    }
  }

  private static class AnalysisContext {
    private final String flow;
    private final String initiator = "IDE";
    private final String orgDisplayName;

    public AnalysisContext(String flow, String orgDisplayName) {
      this.flow = flow;
      this.orgDisplayName = orgDisplayName;
    }

    public String getFlow() {
      return flow;
    }

    public String getOrgDisplayName() {
      return orgDisplayName;
    }

    public String getInitiator() {
      return initiator;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AnalysisContext that = (AnalysisContext) o;
      return Objects.equals(flow, that.flow) && Objects.equals(orgDisplayName, that.orgDisplayName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(flow, orgDisplayName);
    }

    @Override
    public String toString() {
      return "AnalysisContext{" +
        "flow='" + flow + '\'' +
        ", initiator='" + initiator + '\'' +
        ", orgDisplayName='" + orgDisplayName + '\'' +
        '}';
    }
  }
}
