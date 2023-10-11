package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.requests.FileHashRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.javaclient.responses.GetFiltersResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DeepCodeRestApiMock implements DeepCodeRestApi {
  @Override
  public @NotNull CreateBundleResponse createBundle(String orgName, String requestId, FileContentRequest files) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull CreateBundleResponse createBundle(String orgName, String requestId, FileHashRequest files) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull CreateBundleResponse checkBundle(String orgName, String requestId, String bundleId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull <Req> CreateBundleResponse extendBundle(String orgName, String requestId, String bundleId, Req request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull GetAnalysisResponse getAnalysis(
    String orgName,
    String requestId,
    String bundleId,
    Integer severity,
    List<String> filesToAnalyse,
    String shard,
    String ideProductName
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull GetFiltersResponse getFilters() {
    return new GetFiltersResponse();
  }
}
