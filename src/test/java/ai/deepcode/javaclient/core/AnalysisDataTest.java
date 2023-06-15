package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.core.mocks.AnalysisDataBaseMock;
import ai.deepcode.javaclient.core.mocks.DeepCodeParamsMock;
import ai.deepcode.javaclient.core.mocks.DeepCodeRestApiMock;
import ai.deepcode.javaclient.core.mocks.HashContentUtilsMock;
import ai.deepcode.javaclient.core.mocks.LoggerMock;
import ai.deepcode.javaclient.core.mocks.PlatformDependentUtilsMock;
import ai.deepcode.javaclient.requests.FileHashRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.FilesMap;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.javaclient.responses.Suggestions;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;

import static ai.deepcode.javaclient.core.AnalysisDataBase.COMPLETE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnalysisDataTest {

  final PlatformDependentUtilsBase pdUtils = new PlatformDependentUtilsMock() {
    @Override
    protected @NotNull String getProjectBasedFilePath(@NotNull Object file) {
      return "filePath";
    }

    @Override
    public @NotNull String getProjectName(@NotNull Object project) {
      return project.toString();
    }

    @Override
    public @NotNull String getFileName(@NotNull Object file) {
      return "testFileName";
    }

    @Override
    public long getFileSize(@NotNull Object file) {
      return getFileName(file).length();
    }
  };

  final DCLoggerBase dcLogger = new LoggerMock();

  final HashContentUtilsBase hashContentUtils = new HashContentUtilsMock(pdUtils) {
    @Override
    public @NotNull String doGetFileContent(@NotNull Object file) {
      return "file content";
    }
  };

  DeepCodeRestApi restApi;
  DeepCodeParamsBase deepCodeParams;
  AnalysisDataBase analysisData;

  private static class RestApiMockWithBrokenFileUpload extends DeepCodeRestApiMock {
    protected final CreateBundleResponse bundleResponseWithMissedFile =
      new CreateBundleResponse("bundleHash", Collections.singletonList("/filePath"));

    protected final CreateBundleResponse bundleResponseWithoutMissedFile =
      new CreateBundleResponse("bundleHash", Collections.emptyList());

    @Override
    public @NotNull CreateBundleResponse createBundle(String orgName, FileHashRequest files) {
      return bundleResponseWithMissedFile;
    }

    @Override
    public @NotNull <Req> CreateBundleResponse extendBundle(String orgName, String bundleId, Req request) {
      return bundleResponseWithMissedFile;
    }

    @Override
    public @NotNull CreateBundleResponse checkBundle(String orgName, String bundleId) {
      return bundleResponseWithMissedFile;
    }
  }

  // make 3 attempts to re-createBundle if operation does not succeed
  @Test
  public void re_createBundle_if_createBundle_fail_with_404() {
    final int[] reUploadCounter = {0};
    restApi = new RestApiMockWithBrokenFileUpload() {
      @Override
      public @NotNull CreateBundleResponse createBundle(String orgName, FileHashRequest files) {
        reUploadCounter[0] = reUploadCounter[0] + 1;
        return super.createBundle(orgName, files);
      }
    };

    deepCodeParams = new DeepCodeParamsMock(restApi);
    analysisData = new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi);

    // --------------------------- actual test --------------------
    final String project = "Project4";
    final String progress = "Progress Indicator";

    analysisData.updateCachedResultsForFiles(project, Collections.singleton("File"), progress);

    assertEquals("Should be made 3 attempts to re-create bundle if operation does not succeed", 3, reUploadCounter[0]);
  }

  // make 2 attempts to re-createBundle (+1 initial attempt) if extendBundle does not succeed
  @Test
  public void re_createBundle_if_extendBundle_fail_with_404() {
    // create bundle in cache
    restApi = new RestApiMockWithBrokenFileUpload() {
    };
    deepCodeParams = new DeepCodeParamsMock(restApi);
    analysisData = new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi);
    final String project = "Project5";
    final String progress = "Progress Indicator";
    analysisData.updateCachedResultsForFiles(project, Collections.singleton("File"), progress);

    // try to extend expired bundle
    final int[] reUploadCounter = {0};
    restApi = new RestApiMockWithBrokenFileUpload() {
      @Override
      public @NotNull CreateBundleResponse createBundle(String orgName, FileHashRequest files) {
        reUploadCounter[0] = reUploadCounter[0] + 1;
        final CreateBundleResponse response =
          new CreateBundleResponse("bundleHash", Collections.singletonList("/filePath"));
        response.setStatusCode(200);
        return response;
      }

      @Override
      public @NotNull <Req> CreateBundleResponse extendBundle(String orgName, String bundleId, Req request) {
        final CreateBundleResponse response = new CreateBundleResponse();
        response.setStatusCode(404);
        return response;
      }
    };
    analysisData = new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi);

    // --------------------------- actual test --------------------
    analysisData.updateCachedResultsForFiles(project, Collections.singleton("File"), progress);

    assertEquals(
      "Should be made 2 attempts to re-create bundle if extendBundle does not succeed",
      2 + 1, // +1 initial createBundle before failed extendBundle
      reUploadCounter[0]
    );
  }

  // make 10 attempts to re-upload files if operation does not succeed
  @Test
  public void reupload_files_if_initial_upload_does_not_succeed() throws IOException {
    final int[] reUploadCounter = {0};
    restApi = new RestApiMockWithBrokenFileUpload() {
      @Override
      public @NotNull CreateBundleResponse createBundle(String orgName, FileHashRequest files) {
        final CreateBundleResponse response =
          new CreateBundleResponse("bundleHash", Collections.singletonList("/filePath"));
        response.setStatusCode(200);
        return response;
      }

      @Override
      public @NotNull <Req> CreateBundleResponse extendBundle(String orgName, String bundleId, Req request) {
        reUploadCounter[0] = reUploadCounter[0] + 1;
        return super.extendBundle(orgName, bundleId, request);
      }
    };

    deepCodeParams = new DeepCodeParamsMock(restApi);
    analysisData = new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi);

    // --------------------------- actual test --------------------
    final String project = "Project1";
    final String progress = "Progress Indicator";

    File file = File.createTempFile("analysisDataTest", "tmp");
    file.deleteOnExit();
    Files.write(file.toPath(), "testtestest".getBytes(StandardCharsets.UTF_8));

    analysisData.updateCachedResultsForFiles(project, Collections.singleton(file), progress);

    assertEquals("Should have made 10 attempts to re-upload files if operation does not succeed", 10, reUploadCounter[0]);
  }

  // do not try to getAnalysis if `upload files` is not succeed (i.e. `missingFiles` is not empty after uploads)
  @Test
  public void if_file_upload_fail_getAnalysis_should_not_be_invoked() {
    restApi = new RestApiMockWithBrokenFileUpload() {
      @Override
      public @NotNull GetAnalysisResponse getAnalysis(
        String orgName,
        String bundleId,
        Integer severity,
        List<String> filesToAnalyse,
        String shard,
        String ideProductName
      ) {
        throw new RuntimeException("getAnalysis should NOT be invoked");
      }
    };

    deepCodeParams = new DeepCodeParamsMock(restApi);
    analysisData = new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi);

    // --------------------------- actual test --------------------
    final String project = "Project2";
    final String progress = "Progress Indicator";

    analysisData.updateCachedResultsForFiles(project, Collections.singleton("File"), progress);
  }

  // getAnalysis recover (continue polling) if operation sometimes does not succeed with 404
  @Test
  public void getAnalysis_recover_during_polling_if_operation_sometimes_does_not_succeed_with_404() {
    final GetAnalysisResponse completeAnalysisResponse =
      new GetAnalysisResponse(COMPLETE, 1, "", new FilesMap(), new Suggestions());
    completeAnalysisResponse.setStatusCode(200);
    completeAnalysisResponse.setStatusDescription("Fake COMPLETE getAnalysis");

    final GetAnalysisResponse failed404AnalysisResponse = new GetAnalysisResponse();
    failed404AnalysisResponse.setStatusCode(404);
    failed404AnalysisResponse.setStatusDescription("Fake failed 404 getAnalysis");

    final GetAnalysisResponse nonCompleteAnalysisResponse =
      new GetAnalysisResponse("any_non_complete", 0.5, "", new FilesMap(), new Suggestions());
    nonCompleteAnalysisResponse.setStatusCode(200);
    nonCompleteAnalysisResponse.setStatusDescription("Fake non-complete getAnalysis");

    final Queue<GetAnalysisResponse> responses = new LinkedList<>(Arrays.asList(
      nonCompleteAnalysisResponse,
      nonCompleteAnalysisResponse,
      failed404AnalysisResponse,
      nonCompleteAnalysisResponse,
      failed404AnalysisResponse,
      failed404AnalysisResponse,
      completeAnalysisResponse
    ));

    final boolean[] isCompleted = {false};

    restApi = new RestApiMockWithBrokenFileUpload() {
      @Override
      public @NotNull CreateBundleResponse createBundle(String orgName, FileHashRequest files) {
        final CreateBundleResponse response =
          new CreateBundleResponse("bundleHash", Collections.singletonList("/filePath"));
        response.setStatusCode(200);
        return response;
      }

      @Override
      public @NotNull <Req> CreateBundleResponse extendBundle(String orgName, String bundleId, Req request) {
        final CreateBundleResponse response =
          new CreateBundleResponse("bundleHash", Collections.emptyList());
        response.setStatusCode(200);
        return response;
      }

      @Override
      public @NotNull CreateBundleResponse checkBundle(String orgName, String bundleId) {
        final CreateBundleResponse response = new CreateBundleResponse("bundleHash", Collections.emptyList());
        response.setStatusCode(200);
        response.setStatusDescription("Fake successful bundle check");
        return response;
      }

      @Override
      public @NotNull GetAnalysisResponse getAnalysis(
        String orgName,
        String bundleId,
        Integer severity,
        List<String> filesToAnalyse,
        String shard,
        String ideProductName
      ) {
        final GetAnalysisResponse response = Objects.requireNonNull(responses.poll());
        if (response.getStatus().equals(COMPLETE)) {
          isCompleted[0] = true;
        }
        return response;
      }
    };

    deepCodeParams = new DeepCodeParamsMock(restApi) {
      @Override
      public long getTimeoutForGettingAnalysesMs() {
        return 10_000L; // 10 sec
      }
    };
    ;
    analysisData = new AnalysisDataBaseMock(pdUtils, hashContentUtils, deepCodeParams, dcLogger, restApi);

    // --------------------------- actual test --------------------
    final String project = "Project3";
    final String progress = "Progress Indicator";

    analysisData.updateCachedResultsForFiles(project, Collections.singleton("File"), progress);

    assertTrue(
      "Analysis should complete despite getting 404s in the middle of polling",
      isCompleted[0]
    );
  }
}
