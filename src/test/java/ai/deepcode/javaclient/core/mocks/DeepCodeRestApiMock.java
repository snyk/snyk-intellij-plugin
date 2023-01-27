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
  public @NotNull CreateBundleResponse createBundle(String token, FileContentRequest files) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull CreateBundleResponse createBundle(String token, FileHashRequest files) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull CreateBundleResponse checkBundle(String token, String bundleId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull <Req> CreateBundleResponse extendBundle(String token, String bundleId, Req request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull GetAnalysisResponse getAnalysis(
    String token,
    String bundleId,
    Integer severity,
    List<String> filesToAnalyse,
    String shard,
    String ideProductName,
    String orgDisplayName
  ) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull GetFiltersResponse getFilters(String token) {
    return new GetFiltersResponse();
  }
}
