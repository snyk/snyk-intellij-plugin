package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.DeepCodeRestApi;
import ai.deepcode.javaclient.requests.ExtendBundleWithContentRequest;
import ai.deepcode.javaclient.requests.ExtendBundleWithHashRequest;
import ai.deepcode.javaclient.requests.FileContentRequest;
import ai.deepcode.javaclient.requests.FileHash2ContentRequest;
import ai.deepcode.javaclient.requests.FileHashRequest;
import ai.deepcode.javaclient.responses.CreateBundleResponse;
import ai.deepcode.javaclient.responses.EmptyResponse;
import ai.deepcode.javaclient.responses.FilePosition;
import ai.deepcode.javaclient.responses.FileSuggestions;
import ai.deepcode.javaclient.responses.FilesMap;
import ai.deepcode.javaclient.responses.GetAnalysisResponse;
import ai.deepcode.javaclient.responses.Marker;
import ai.deepcode.javaclient.responses.Position;
import ai.deepcode.javaclient.responses.Suggestion;
import ai.deepcode.javaclient.responses.Suggestions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public abstract class AnalysisDataBase {

  public static final String COMPLETE = "COMPLETE";
  public static final int MAX_FILE_SIZE = 1024 * 1024;
  private final PlatformDependentUtilsBase pdUtils;
  private final HashContentUtilsBase hashContentUtils;
  private final DeepCodeParamsBase deepCodeParams;
  private final DCLoggerBase dcLogger;
  private final DeepCodeRestApi restApi;

  protected AnalysisDataBase(
    @NotNull PlatformDependentUtilsBase platformDependentUtils,
    @NotNull HashContentUtilsBase hashContentUtils,
    @NotNull DeepCodeParamsBase deepCodeParams,
    @NotNull DCLoggerBase dcLogger,
    @NotNull DeepCodeRestApi restApi
  ) {
    this.pdUtils = platformDependentUtils;
    this.hashContentUtils = hashContentUtils;
    this.deepCodeParams = deepCodeParams;
    this.dcLogger = dcLogger;
    UPLOADING_FILES_TEXT = dcLogger.presentableName + ": Uploading files to the server... ";
    PREPARE_FILES_TEXT = dcLogger.presentableName + ": Preparing files for upload... ";
    WAITING_FOR_ANALYSIS_TEXT = dcLogger.presentableName + ": Waiting for analysis from server... ";
    this.restApi = restApi;
  }

  private final String UPLOADING_FILES_TEXT;
  private final String PREPARE_FILES_TEXT;
  private final String WAITING_FOR_ANALYSIS_TEXT;

  private static final Map<Object, List<SuggestionForFile>> EMPTY_MAP = Collections.emptyMap();
  private static final Map<Object, String> mapProject2analysisUrl = new ConcurrentHashMap<>();

  // todo: keep few latest file versions (Guava com.google.common.cache.CacheBuilder ?)
  private static final Map<Object, List<SuggestionForFile>> mapFile2Suggestions =
    new ConcurrentHashMap<>();

  private static final Map<Object, String> mapProject2BundleId = new ConcurrentHashMap<>();

  // Mutex need to be requested to change mapFile2Suggestions
  private static final ReentrantLock MUTEX = new ReentrantLock();

  /**
   * see getAnalysis() below}
   */
  @NotNull
  public List<SuggestionForFile> getAnalysis(@NotNull Object file) {
    return getAnalysis(Collections.singleton(file)).getOrDefault(file, Collections.emptyList());
  }

  /**
   * Return Suggestions mapped to Files.
   *
   * <p>Look into cached results ONLY.
   */
  @NotNull
  public Map<Object, List<SuggestionForFile>> getAnalysis(@NotNull Collection<Object> files) {
    if (files.isEmpty()) {
      dcLogger.logWarn("getAnalysis requested for empty list of files");
      return Collections.emptyMap();
    }
    Map<Object, List<SuggestionForFile>> result = new HashMap<>();
    final Collection<Object> brokenKeys = new ArrayList<>();
    for (Object file : files) {
      List<SuggestionForFile> suggestions = mapFile2Suggestions.get(file);
      if (suggestions != null) {
        result.put(file, suggestions);
      } else {
        brokenKeys.add(file);
      }
    }
    if (!brokenKeys.isEmpty()) {
      dcLogger.logWarn("Suggestions not found for " + brokenKeys.size() + " files: " + brokenKeys);
    }
    return result;
  }

  public String getAnalysisUrl(@NotNull Object project) {
    return mapProject2analysisUrl.computeIfAbsent(project, p -> "");
  }

  public boolean addProjectToCache(@NotNull Object project) {
    return mapProject2BundleId.putIfAbsent(project, "") == null;
  }

  public Set<Object> getAllCachedProject() {
    return mapProject2BundleId.keySet();
  }

  private static final Map<Object, Set<Object>> mapProject2RemovedFiles = new ConcurrentHashMap<>();

  public void removeFilesFromCache(@NotNull Collection<Object> files) {
    try {
      final List<String> first50FilesName =
        files.stream().limit(50).map(pdUtils::getFileName).collect(Collectors.toList());
      dcLogger.logInfo(
        "Request to remove from cache " + files.size() + " files: " + first50FilesName);
      // todo: do we really need mutex here?
      MUTEX.lock();
      dcLogger.logInfo("MUTEX LOCK, hold count = " + MUTEX.getHoldCount());
      int removeCounter = 0;
      for (Object file : files) {
        if (file != null && isFileInCache(file)) {
          mapFile2Suggestions.remove(file);
          mapProject2RemovedFiles
            .computeIfAbsent(pdUtils.getProject(file), p -> new HashSet<>())
            .add(file);
          hashContentUtils.removeFileHashContent(file);
          removeCounter++;
        }
      }
      dcLogger.logInfo(
        "Actually removed from cache: "
          + removeCounter
          + " files. Were not in cache: "
          + (files.size() - removeCounter));
    } finally {
      MUTEX.unlock();
      dcLogger.logInfo("MUTEX RELEASED, hold count = " + MUTEX.getHoldCount());
    }
    updateUIonFilesRemovalFromCache(files);
  }

  protected abstract void updateUIonFilesRemovalFromCache(@NotNull Collection<Object> files);

  public void removeProjectFromCaches(@NotNull Object project) {
    dcLogger.logInfo("Caches clearance requested for project: " + project);
    hashContentUtils.removeProjectHashContent(project);
    if (mapProject2BundleId.remove(project) != null) {
      dcLogger.logInfo("Removed from cache: " + project);
    }
    removeFilesFromCache(cachedFilesOfProject(project));
    mapProject2RemovedFiles.remove(project);
  }

  private Collection<Object> cachedFilesOfProject(@NotNull Object project) {
    return mapFile2Suggestions.keySet().stream()
      .filter(file -> pdUtils.getProject(file).equals(project))
      .collect(Collectors.toList());
  }

  private static final Set<Object> updateInProgress = Collections.synchronizedSet(new HashSet<>());

  private void setUpdateInProgress(@NotNull Object project) {
    synchronized (updateInProgress) {
      updateInProgress.add(project);
    }
  }

  private void unsetUpdateInProgress(@NotNull Object project) {
    synchronized (updateInProgress) {
      updateInProgress.remove(project);
    }
  }

  public boolean isUpdateAnalysisInProgress(@NotNull Object project) {
    synchronized (updateInProgress) {
      return updateInProgress.contains(project);
    }
  }

  public boolean isProjectNOTAnalysed(@NotNull Object project) {
    return !getAllCachedProject().contains(project);
  }

  public void waitForUpdateAnalysisFinish(@NotNull Object project, @Nullable Object progress) {
    while (isUpdateAnalysisInProgress(project)) {
      // delay should be less or equal to runInBackgroundCancellable delay
      pdUtils.delay(PlatformDependentUtilsBase.DEFAULT_DELAY_SMALL, progress);
    }
  }

  public void updateCachedResultsForFiles(
    @NotNull Object project,
    @NotNull Collection<Object> allProjectFiles,
    @NotNull Object progress,
    @NotNull String requestId) {
    Collection<Object> filesToRemove = mapProject2RemovedFiles.remove(project);
    if (filesToRemove == null) {
      filesToRemove = Collections.emptyList();
    } else {
      // remove from server only files physically removed from Project
      filesToRemove.removeAll(allProjectFiles);
    }
    if (allProjectFiles.isEmpty() && filesToRemove.isEmpty()) {
      dcLogger.logWarn("updateCachedResultsForFiles requested for empty list of files");
      return;
    }
    final List<String> first50FilesName =
      allProjectFiles.stream().limit(50).map(pdUtils::getFileName).collect(Collectors.toList());
    dcLogger.logInfo(
      "Update requested for " + allProjectFiles.size() + " files: " + first50FilesName);
    if (!deepCodeParams.consentGiven(project)) {
      dcLogger.logWarn("Consent check fail! Project: " + pdUtils.getProjectName(project));
      return;
    }
    try {
      MUTEX.lock();
      dcLogger.logInfo("MUTEX LOCK, hold count = " + MUTEX.getHoldCount());
      setUpdateInProgress(project);
      Collection<Object> filesToProceed =
        allProjectFiles.stream()
          .filter(Objects::nonNull)
          .filter(file -> !mapFile2Suggestions.containsKey(file))
          .collect(Collectors.toSet());
      if (!filesToProceed.isEmpty()) {
        // collection already checked to be not empty
        final Object firstFile = filesToProceed.iterator().next();
        final String fileHash = hashContentUtils.getHash(firstFile);
        dcLogger.logInfo(
          "Files to proceed (not found in cache): "
            + filesToProceed.size()
            + "\nHash for first file "
            + pdUtils.getFileName(firstFile)
            + " ["
            + fileHash
            + "]");
      } else if (!filesToRemove.isEmpty()) {
        dcLogger.logWarn(
          "Nothing to update for " + allProjectFiles.size() + " files: " + allProjectFiles);
      }
      if (!filesToRemove.isEmpty()) {
        dcLogger.logInfo("Files to remove: " + filesToRemove.size() + " files: " + filesToRemove);
      }
      mapFile2Suggestions.putAll(
        retrieveSuggestions(project, filesToProceed, filesToRemove, progress, requestId)
      );
    } catch (BundleIdExpire404Exception e) {
      // re-try to create bundle from scratch
      retryFullUpdateCachedResults(project, allProjectFiles, progress, 2, requestId);
    } finally {
      MUTEX.unlock();
      dcLogger.logInfo("MUTEX RELEASED, hold count = " + MUTEX.getHoldCount());
      unsetUpdateInProgress(project);
      pdUtils.refreshPanel(project);
    }
  }

  private void retryFullUpdateCachedResults(
    @NotNull Object project,
    @NotNull Collection<Object> allProjectFiles,
    @NotNull Object progress,
    int attemptCounter,
    @NotNull String requestId
  ) {
    if (attemptCounter <= 0) {
      showWarnIfNeeded(project, "Operations with bundle failed. Please try again later or contact Snyk support");
      return;
    }
    removeProjectFromCaches(project);
    try {
      mapFile2Suggestions.putAll(
        retrieveSuggestions(project, allProjectFiles, Collections.emptyList(), progress, requestId)
      );
    } catch (BundleIdExpire404Exception ex) {
      retryFullUpdateCachedResults(project, allProjectFiles, progress, attemptCounter - 1, requestId);
    }
  }

  private static class TokenInvalid401Exception extends Exception {
  }

  private static class BundleIdExpire404Exception extends Exception {
  }

  private static class ApiCallNotSucceedException extends Exception {
  }

  private static final Set<Object> projectsLoginRequested = ConcurrentHashMap.newKeySet();
  private static final Set<Object> projectsWithNotSucceedWarnShown = ConcurrentHashMap.newKeySet();

  private void showWarnIfNeeded(@NotNull Object project, @NotNull String message) {
    pdUtils.showWarn(message, project, !projectsWithNotSucceedWarnShown.add(project));
  }

  private void checkApiCallSucceed(
    @NotNull Object project, EmptyResponse response, String internalMessage
  ) throws ApiCallNotSucceedException, TokenInvalid401Exception, BundleIdExpire404Exception {
    if (response.getStatusCode() == 200) {
      projectsWithNotSucceedWarnShown.remove(project);
      projectsLoginRequested.remove(project);
    } else {
      final String fullLogMessage =
        internalMessage + response.getStatusCode() + " " + response.getStatusDescription();
      dcLogger.logWarn(fullLogMessage);
      if (response.getStatusCode() == 401) {
        pdUtils.isLogged(project, !projectsLoginRequested.add(project));
        throw new TokenInvalid401Exception();
      } else if (response.getStatusCode() == 404) {
        throw new BundleIdExpire404Exception();
      } else {
        throw new ApiCallNotSucceedException();
      }
    }
  }

  static final int MAX_BUNDLE_SIZE = 4000000; // bytes

  /**
   * Perform costly network request. <b>No cache checks!</b>
   */
  @NotNull
  private Map<Object, List<SuggestionForFile>> retrieveSuggestions(
    @NotNull Object project,
    @NotNull Collection<Object> filesToProceed,
    @NotNull Collection<Object> filesToRemove,
    @NotNull Object progress,
    @NotNull String requestId
  ) throws BundleIdExpire404Exception {
    if (filesToProceed.isEmpty() && filesToRemove.isEmpty()) {
      dcLogger.logWarn("Both filesToProceed and filesToRemove are empty");
      return EMPTY_MAP;
    }
    // no needs to check login here as it will be checked anyway during every api response's check
    // if (!LoginUtils.isLogged(project, false)) return EMPTY_MAP;

    List<String> missingFiles;
    try {
      missingFiles = createBundleStep(project, filesToProceed, filesToRemove, progress, requestId);
    } catch (ApiCallNotSucceedException e) {
      // re-try createBundleStep from scratch for few times, i.e. do the same as if parent bundle is expired
      mapProject2BundleId.put(project, "");
      throw new BundleIdExpire404Exception();
    } catch (TokenInvalid401Exception e) {
      return EMPTY_MAP;
    }

    if (filesToProceed.isEmpty()) { // no sense to proceed
      return EMPTY_MAP;
    }
    boolean filesUploaded = uploadFilesStep(project, filesToProceed, missingFiles, progress, requestId);
    if (!filesUploaded) { // no sense to proceed
      return EMPTY_MAP;
    }

    // ---------------------------------------- Get Analysis
    final String bundleId = mapProject2BundleId.getOrDefault(project, "");
    if (bundleId.isEmpty()) { // no sense to proceed
      return EMPTY_MAP;
    }
    long startTime = System.currentTimeMillis();
    pdUtils.progressSetText(progress, WAITING_FOR_ANALYSIS_TEXT);
    pdUtils.progressCheckCanceled(progress);
    List<String> filesToAnalyse =
      filesToProceed.stream().map(pdUtils::getDeepCodedFilePath).collect(Collectors.toList());
    GetAnalysisResponse getAnalysisResponse;
    try {
      getAnalysisResponse = doGetAnalysis(project, bundleId, progress, filesToAnalyse, requestId);
    } catch (TokenInvalid401Exception e) {
      return EMPTY_MAP;
    }
    Map<Object, List<SuggestionForFile>> result =
      parseGetAnalysisResponse(project, filesToProceed, getAnalysisResponse, progress);
    dcLogger.logInfo(
      "--- Get Analysis took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    return result;
  }

  /**
   * Perform costly network request. <b>No cache checks!</b>
   *
   * @return missingFiles
   */
  private List<String> createBundleStep(
    @NotNull Object project,
    @NotNull Collection<Object> filesToProceed,
    @NotNull Collection<Object> filesToRemove,
    @NotNull Object progress,
    @NotNull String requestId
  ) throws BundleIdExpire404Exception, ApiCallNotSucceedException, TokenInvalid401Exception {
    long startTime = System.currentTimeMillis();
    pdUtils.progressSetText(progress, PREPARE_FILES_TEXT);
    dcLogger.logInfo(PREPARE_FILES_TEXT);
    pdUtils.progressCheckCanceled(progress);
    FileHashRequest hashRequest = new FileHashRequest();
    long sizePath2Hash = 0;
    int fileCounter = 0;
    int totalFiles = filesToProceed.size();
    for (Object file : filesToProceed) {
      hashContentUtils.removeFileHashContent(file);
      pdUtils.progressCheckCanceled(progress);
      pdUtils.progressSetFraction(progress, ((double) fileCounter++) / totalFiles);
      pdUtils.progressSetText(
        progress, PREPARE_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");

      final String path = pdUtils.getDeepCodedFilePath(file);

      // info("getHash requested");
      final String hash = hashContentUtils.getHash(file);
      if (fileCounter == 1)
        dcLogger.logInfo("First file to process: \npath = " + path + "\nhash = " + hash);

      hashRequest.put(path, hash);
      sizePath2Hash += (path.length() + hash.length()) * 2L; // rough estimation of bytes occupied
      if (sizePath2Hash > MAX_BUNDLE_SIZE) {
        CreateBundleResponse tempBundleResponse =
          makeNewBundle(project, hashRequest, Collections.emptyList(), requestId);
        sizePath2Hash = 0;
        hashRequest.clear();
      }
    }
    // todo break removeFiles in chunks less then MAX_BANDLE_SIZE
    //  needed ?? we do full rescan for large amount of files to remove
    CreateBundleResponse createBundleResponse = makeNewBundle(project, hashRequest, filesToRemove, requestId);

    final String bundleId = createBundleResponse.getBundleHash();

    List<String> missingFiles = createBundleResponse.getMissingFiles();
    dcLogger.logInfo(
      "--- Create/Extend Bundle took: "
        + (System.currentTimeMillis() - startTime)
        + " milliseconds"
        + "\nbundleId: "
        + bundleId
        + "\nmissingFiles: "
        + missingFiles.size());
    return missingFiles;
  }

  /**
   * Perform costly network request. <b>No cache checks!</b>
   */
  private boolean uploadFilesStep(
    @NotNull Object project,
    @NotNull Collection<Object> filesToProceed,
    @NotNull List<String> missingFiles,
    @NotNull Object progress,
    @NotNull String requestId
  ) throws BundleIdExpire404Exception {
    long startTime = System.currentTimeMillis();
    pdUtils.progressSetText(progress, UPLOADING_FILES_TEXT);
    pdUtils.progressCheckCanceled(progress);

    final String bundleId = mapProject2BundleId.getOrDefault(project, "");
    if (bundleId.isEmpty()) {
      dcLogger.logInfo("BundleId is empty");
    } else if (missingFiles.isEmpty()) {
      dcLogger.logInfo("No missingFiles to Upload");
    } else {
      final int attempts = 10;
      int counter = 0;
      while (!missingFiles.isEmpty() && counter < attempts) {
        if (counter > 0) {
          dcLogger.logWarn(
            "Check Bundle found "
              + missingFiles.size()
              + " missingFiles (NOT uploaded), will try to re-upload "
              + (attempts - counter)
              + " more times:\nmissingFiles = "
              + missingFiles);
        }
        List<String> newMissingFiles;
        try {
          uploadFiles(project, filesToProceed, missingFiles, bundleId, progress, requestId);
          newMissingFiles = checkBundle(project, bundleId, requestId);
        } catch (TokenInvalid401Exception e) {
          break;
        } catch (ApiCallNotSucceedException e) {
          newMissingFiles = missingFiles;
        }
        missingFiles = newMissingFiles;
        counter++;
      }
      if (counter >= attempts) {
        showWarnIfNeeded(project, "Failed to upload files. Please try again later or contact Snyk support");
      }
    }
    dcLogger.logInfo(
      "--- Upload Files took: " + (System.currentTimeMillis() - startTime) + " milliseconds");
    return missingFiles.isEmpty();
  }

  private void uploadFiles(
    @NotNull Object project,
    @NotNull Collection<Object> filesToProceed,
    @NotNull List<String> missingFiles,
    @NotNull String bundleId,
    @NotNull Object progress,
    @NotNull String requestId
  ) throws ApiCallNotSucceedException, TokenInvalid401Exception, BundleIdExpire404Exception {
    Map<String, Object> mapPath2File =
      filesToProceed.stream().collect(Collectors.toMap(pdUtils::getDeepCodedFilePath, it -> it));
    int fileCounter = 0;
    int totalFiles = missingFiles.size();
    long fileChunkSize = 0;
    int brokenMissingFilesCount = 0;
    String brokenMissingFilesMessage = "";
    List<Object> filesChunk = new ArrayList<>();
    for (String filePath : missingFiles) {
      pdUtils.progressCheckCanceled(progress);
      pdUtils.progressSetFraction(progress, ((double) fileCounter++) / totalFiles);
      pdUtils.progressSetText(
        progress, UPLOADING_FILES_TEXT + fileCounter + " of " + totalFiles + " files done.");

      Object file = mapPath2File.get(filePath);
      if (file == null) {
        if (brokenMissingFilesCount == 0) {
          brokenMissingFilesMessage =
            " files requested in missingFiles not found in filesToProceed (skipped to upload)."
              + "\nFirst broken missingFile: "
              + filePath;
        }
        brokenMissingFilesCount++;
        continue;
      }
      final long fileSize = pdUtils.getFileSize(file); // .getVirtualFile().getLength();
      if (fileChunkSize + fileSize > MAX_BUNDLE_SIZE) {
        dcLogger.logInfo("Files-chunk size: " + fileChunkSize);
        doUploadFiles(project, filesChunk, bundleId, progress, requestId);
        fileChunkSize = 0;
        filesChunk.clear();
      }
      fileChunkSize += fileSize;
      filesChunk.add(file);
    }
    if (brokenMissingFilesCount > 0)
      dcLogger.logWarn(brokenMissingFilesCount + brokenMissingFilesMessage);
    dcLogger.logInfo("Last filesToProceed-chunk size: " + fileChunkSize);
    doUploadFiles(project, filesChunk, bundleId, progress, requestId);
  }

  /**
   * Checks the status of a bundle: if there are still missing files after uploading
   *
   * @return list of the current missingFiles or NULL if not succeed.
   */
  private List<String> checkBundle(
    @NotNull Object project,
    @NotNull String bundleId,
    @NotNull String requestId
  ) throws TokenInvalid401Exception, BundleIdExpire404Exception, ApiCallNotSucceedException {
    CreateBundleResponse checkBundleResponse =
      restApi.checkBundle(deepCodeParams.getOrgDisplayName(), requestId, bundleId);
    checkApiCallSucceed(project, checkBundleResponse, "Bad CheckBundle request: ");
    return checkBundleResponse.getMissingFiles();
  }

  private CreateBundleResponse makeNewBundle(
    @NotNull Object project,
    @NotNull FileHashRequest request,
    @NotNull Collection<Object> filesToRemove,
    @NotNull String requestId
  ) throws BundleIdExpire404Exception, ApiCallNotSucceedException, TokenInvalid401Exception {
    final String parentBundleId = mapProject2BundleId.getOrDefault(project, "");
    if (!parentBundleId.isEmpty()
      && !filesToRemove.isEmpty()
      && request.isEmpty()
      && filesToRemove.containsAll(cachedFilesOfProject(project))) {
      dcLogger.logWarn(
        "Attempt to Extending a bundle by removing all the parent bundle's files: "
          + filesToRemove);
    }
    List<String> removedFiles =
      filesToRemove.stream().map(pdUtils::getDeepCodedFilePath).collect(Collectors.toList());
    String message =
      (parentBundleId.isEmpty()
        ? "Creating new Bundle with "
        : "Extending existing Bundle [" + parentBundleId + "] with ")
        + request.size()
        + " files"
        + (removedFiles.isEmpty() ? "" : " and remove " + removedFiles.size() + " files");
    dcLogger.logInfo(message);
    // todo make network request in parallel with collecting data
    final CreateBundleResponse bundleResponse;
    // check if bundleID for the project already been created
    if (parentBundleId.isEmpty())
      bundleResponse = restApi.createBundle(deepCodeParams.getOrgDisplayName(), requestId, request);
    else {
      bundleResponse =
        restApi.extendBundle(
          deepCodeParams.getOrgDisplayName(),
          requestId,
          parentBundleId,
          new ExtendBundleWithHashRequest(request, removedFiles));
    }
    String newBundleId = bundleResponse.getBundleHash();
    // assigned to every bundle that contains no files
    if (newBundleId.endsWith("0000000000000000000000000000000000000000000000000000000000000000")) {
      newBundleId = "";
    }
    checkApiCallSucceed(project, bundleResponse, "Bad Create/Extend Bundle request: ");
    mapProject2BundleId.put(project, newBundleId);
    return bundleResponse;
  }

  private void doUploadFiles(
    @NotNull Object project,
    @NotNull Collection<Object> psiFiles,
    @NotNull String bundleId,
    @NotNull Object progress,
    @NotNull String requestId
  ) throws ApiCallNotSucceedException, TokenInvalid401Exception, BundleIdExpire404Exception {
    dcLogger.logInfo("Uploading " + psiFiles.size() + " files... ");
    if (psiFiles.isEmpty()) return;

    FileContentRequest files = new FileContentRequest();
    for (Object psiFile : psiFiles) {
      pdUtils.progressCheckCanceled(progress);
      files.put(
        pdUtils.getFilePath(psiFile),
        new FileHash2ContentRequest(
          hashContentUtils.getHash(psiFile), hashContentUtils.getFileContent(psiFile)));
    }

    // todo make network request in parallel with collecting data
    EmptyResponse uploadFilesResponse =
      restApi.extendBundle(
        deepCodeParams.getOrgDisplayName(),
        requestId,
        bundleId,
        new ExtendBundleWithContentRequest(files, Collections.emptyList()));
    checkApiCallSucceed(project, uploadFilesResponse, "Bad UploadFiles request: ");
  }

  @NotNull
  private GetAnalysisResponse doGetAnalysis(
    @NotNull Object project,
    @NotNull String bundleId,
    @NotNull Object progress,
    List<String> filesToAnalyse,
    @NotNull String requestId
  ) throws TokenInvalid401Exception {
    GetAnalysisResponse response;
    int counter = 0;
    int skippedFailsCount = 0;
    final long timeout = deepCodeParams.getTimeoutForGettingAnalysesMs();
    final long attempts = timeout / PlatformDependentUtilsBase.DEFAULT_DELAY;
    final long endTime = System.currentTimeMillis() + timeout;
    do {
      if (counter > 0) pdUtils.delay(PlatformDependentUtilsBase.DEFAULT_DELAY, progress);
      response =
        restApi.getAnalysis(
          deepCodeParams.getOrgDisplayName(),
          requestId,
          bundleId,
          deepCodeParams.getMinSeverity(),
          filesToAnalyse,
          HashContentUtilsBase.calculateHash(pdUtils.getProjectName(project)),
          deepCodeParams.getIdeProductName());

      pdUtils.progressCheckCanceled(progress);
      dcLogger.logInfo(response.toString());

      try {
        checkApiCallSucceed(project, response, "Bad GetAnalysis request: ");
        skippedFailsCount = 0;
      } catch (BundleIdExpire404Exception | ApiCallNotSucceedException e) {
        if (skippedFailsCount >= 5) {
          showWarnIfNeeded(project, "Failed to get analysis results. Please try again later or contact Snyk support");
          return new GetAnalysisResponse();
        } else {
          skippedFailsCount++;
        }
      }
      double responseProgress = response.getProgress();
      if (responseProgress <= 0 || responseProgress > 1) {
        responseProgress = ((double) counter) / attempts;
      }
      pdUtils.progressSetFraction(progress, responseProgress);
      pdUtils.progressSetText(
        progress, WAITING_FOR_ANALYSIS_TEXT + (int) (responseProgress * 100) + "% done");

      if (System.currentTimeMillis() >= endTime) {
        dcLogger.logWarn("Timeout expire for waiting analysis results.");
        showWarnIfNeeded(
          project,
          "Can't get analysis results from the server. Timeout of "
            + timeout / 1000
            + " sec. is reached."
            + " Please, increase timeout or try again later."
        );
        break;
      }

      if (response.getStatus().equals("FAILED")) {
        dcLogger.logWarn("FAILED getAnalysis request.");
        // if Failed then we have inconsistent caches, better to do full rescan
        pdUtils.doFullRescan(project);
        /*if (!RunUtils.isFullRescanRequested(project)) {
          RunUtils.rescanInBackgroundCancellableDelayed(project, 500, false);
        }*/
        break;
      }

      counter++;
    } while (!response.getStatus().equalsIgnoreCase(COMPLETE)
      // !!!! keep commented in production, for debug only: to emulate long processing
      // || counter < 10
    );
    return response;
  }

  @NotNull
  private Map<Object, List<SuggestionForFile>> parseGetAnalysisResponse(
    @NotNull Object project,
    @NotNull Collection<Object> files,
    GetAnalysisResponse response,
    @NotNull Object progress) {
    Map<Object, List<SuggestionForFile>> result = new HashMap<>();
    if (!response.getStatus().equalsIgnoreCase(COMPLETE)) return EMPTY_MAP;
    mapProject2analysisUrl.put(project, response.getAnalysisURL());
    FilesMap filesMap = response.getFiles();
    if (filesMap == null || filesMap.isEmpty()) {
      dcLogger.logWarn("AnalysisResults is null for: " + response);
      return EMPTY_MAP;
    }
    for (Object file : files) {
      // fixme iterate over analysisResults.getFiles() to reduce empty passes
      final String deepCodedFilePath = pdUtils.getDeepCodedFilePath(file);
      FileSuggestions fileSuggestions = filesMap.get(deepCodedFilePath);
      if (fileSuggestions == null) {
        result.put(file, Collections.emptyList());
        continue;
      }
      final Suggestions suggestions = response.getSuggestions();
      if (suggestions == null) {
        dcLogger.logWarn("Suggestions is empty for: " + response);
        return EMPTY_MAP;
      }
      pdUtils.progressCheckCanceled(progress);

      final List<SuggestionForFile> mySuggestions = new ArrayList<>();
      for (String suggestionIndex : fileSuggestions.keySet()) {
        final Suggestion suggestion = suggestions.get(suggestionIndex);
        if (suggestion == null) {
          dcLogger.logWarn(
            "Suggestion not found for suggestionIndex: "
              + suggestionIndex
              + "\nGetAnalysisResponse: "
              + response);
          return EMPTY_MAP;
        }

        final List<MyTextRange> ranges = new ArrayList<>();
        for (FilePosition filePosition : fileSuggestions.get(suggestionIndex)) {

          final Map<MyTextRange, List<MyTextRange>> markers =
            new LinkedHashMap<>(); // order should be preserved
          for (Marker marker : filePosition.getMarkers()) {
            final MyTextRange msgRange =
              new MyTextRange(marker.getMsg().get(0), marker.getMsg().get(1) + 1);
            final List<MyTextRange> positions =
              marker.getPos().stream()
                .map(
                  it -> {
                    final Object fileForMarker =
                      (it.getFile() == null || it.getFile().isEmpty())
                        ? file
                        : pdUtils.getFileByDeepcodedPath(it.getFile(), project);
                    if (fileForMarker == null) {
                      dcLogger.logWarn("File not found for marker: " + it);
                      return null;
                    }
                    return parsePosition2MyTextRange(
                      it, fileForMarker, Collections.emptyMap());
                  })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            markers.put(msgRange, positions);
          }
          final MyTextRange suggestionRange =
            parsePosition2MyTextRange(filePosition, file, markers);
          if (suggestionRange != null) ranges.add(suggestionRange);
        }

        mySuggestions.add(
          new SuggestionForFile(
            suggestion.getId(),
            suggestion.getRule(),
            suggestion.getMessage(),
            suggestion.getTitle(),
            suggestion.getText(),
            suggestion.getSeverity(),
            suggestion.getRepoDatasetSize(),
            suggestion.getExampleCommitDescriptions(),
            suggestion.getExampleCommitFixes(),
            ranges,
            suggestion.getCategories(),
            suggestion.getTags(),
            suggestion.getCwe()));
      }
      result.put(file, mySuggestions);
    }
    return result;
  }

  @Nullable
  private MyTextRange parsePosition2MyTextRange(
    @NotNull final Position position,
    @NotNull final Object file,
    @NotNull final Map<MyTextRange, List<MyTextRange>> markers) {

    final int startRow = position.getRows().get(0);
    final int endRow = position.getRows().get(1);
    final int startCol = position.getCols().get(0) - 1; // inclusive
    final int endCol = position.getCols().get(1);

    if (startRow <= 0 || endRow <= 0 || startCol < 0 || endCol < 0) {
      final String deepCodedFilePath = pdUtils.getDeepCodedFilePath(file);
      dcLogger.logWarn("Incorrect " + position + "\nin file: " + deepCodedFilePath);
      return null;
    }

    final int mLineStartOffset = pdUtils.getLineStartOffset(file, startRow - 1); // to 0-based
    final int mLineEndOffset = pdUtils.getLineStartOffset(file, endRow - 1);

    return new MyTextRange(
      mLineStartOffset + startCol,
      mLineEndOffset + endCol,
      startRow,
      endRow,
      startCol,
      endCol,
      markers,
      position.getFile());
  }

  public Set<Object> getAllFilesWithSuggestions(@NotNull final Object project) {
    return mapFile2Suggestions.entrySet().stream()
      .filter(e -> pdUtils.getProject(e.getKey()).equals(project))
      .filter(e -> !e.getValue().isEmpty())
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
  }

  public Set<Object> getAllCachedFiles(@NotNull final Object project) {
    return mapFile2Suggestions.keySet().stream()
      .filter(file -> pdUtils.getProject(file).equals(project))
      .collect(Collectors.toSet());
  }

  public boolean isFileInCache(@NotNull Object psiFile) {
    return mapFile2Suggestions.containsKey(psiFile);
  }

  /**
   * Remove project from all Caches and <b>CANCEL</b> all background tasks for it
   */
  public void resetCachesAndTasks(@Nullable final Object project) {
    final Set<Object> projects =
      (project == null) ? getAllCachedProject() : Collections.singleton(project);
    for (Object prj : projects) {
      // lets all running ProgressIndicators release MUTEX first
      pdUtils.cancelRunningIndicators(prj);
      removeProjectFromCaches(prj);
      pdUtils.refreshPanel(prj); // ServiceManager.getService(prj, myTodoView.class).refresh();
      mapProject2analysisUrl.put(prj, "");
    }
  }
}
