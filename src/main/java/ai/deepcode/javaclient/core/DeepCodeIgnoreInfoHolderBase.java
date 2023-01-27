package ai.deepcode.javaclient.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;

public abstract class DeepCodeIgnoreInfoHolderBase {

  private final HashContentUtilsBase hashContentUtils;
  private final PlatformDependentUtilsBase pdUtils;
  private final DCLoggerBase dcLogger;

  // .ignore file to Line in .ignore file to PathMatcher
  private final Map<Object, Map<Integer, PathMatcher>> map_ignore2PathMatchers =
    new ConcurrentHashMap<>();

  // .ignore file to Line in .ignore file to PathMatcher
  private final Map<Object, Map<Integer, PathMatcher>> map_ignore2ReIncludePathMatchers =
    new ConcurrentHashMap<>();

  private final Map<Object, Map<String, Boolean>> project2IgnoredFilePaths =
    new ConcurrentHashMap<>();

  protected DeepCodeIgnoreInfoHolderBase(
    @NotNull HashContentUtilsBase hashContentUtils,
    @NotNull PlatformDependentUtilsBase pdUtils,
    @NotNull DCLoggerBase dcLogger) {
    this.hashContentUtils = hashContentUtils;
    this.pdUtils = pdUtils;
    this.dcLogger = dcLogger;
  }

  public void scanAllMissedIgnoreFiles(
    @NotNull Collection<Object> allProjectFiles, @Nullable Object progress) {
    allProjectFiles.stream()
      .filter(this::is_ignoreFile)
      .filter(ignoreFile -> !map_ignore2PathMatchers.containsKey(ignoreFile))
      .forEach(ignoreFile -> update_ignoreFileContent(ignoreFile, progress));
  }

  public boolean isIgnoredFile(@NotNull Object fileToCheck) {
    return project2IgnoredFilePaths
      .computeIfAbsent(pdUtils.getProject(fileToCheck), prj -> new ConcurrentHashMap<>())
      .computeIfAbsent(
        pdUtils.getFilePath(fileToCheck),
        filePath ->
          map_ignore2PathMatchers.keySet().stream()
            .filter(ignoreFile -> inScope(filePath, ignoreFile))
            .anyMatch(ignoreFile -> isIgnoredFile(filePath, ignoreFile)));
  }

  private boolean isIgnoredFile(@NotNull String filePath, @NotNull Object ignoreFile) {
    final Path path = pathOf(filePath);
    return map_ignore2PathMatchers.get(ignoreFile).entrySet().stream()
      .anyMatch(
        line2matcher -> {
          final int lineIndex = line2matcher.getKey();
          final PathMatcher pathMatcher = line2matcher.getValue();
          return pathMatcher.matches(path)
            &&
            // An optional prefix "!" which negates the pattern;
            // any matching file excluded by a _previous_ pattern will become included again.
            map_ignore2ReIncludePathMatchers.get(ignoreFile).entrySet().stream()
              .filter(e -> e.getKey() > lineIndex)
              .noneMatch(e -> e.getValue().matches(path));
        });
  }

  private void removeIgnoredFilePaths(@NotNull Object ignoreFile) {
    final Object project = pdUtils.getProject(ignoreFile);
    project2IgnoredFilePaths
      .getOrDefault(project, Collections.emptyMap())
      .keySet()
      .removeIf(filePath -> inScope(filePath, ignoreFile));
  }

  /**
   * copy of {@link Path#of(String, String...)} due to java 8 compatibility
   */
  private static Path pathOf(String first, String... more) {
    return FileSystems.getDefault().getPath(first, more);
  }

  private boolean inScope(@NotNull String filePathToCheck, @NotNull Object ignoreFile) {
    return filePathToCheck.startsWith(pdUtils.getDirPath(ignoreFile));
  }

  ;

  public boolean is_ignoreFile(@NotNull Object file) {
    return is_dcignoreFile(file) || is_gitignoreFile(file);
  }

  public boolean is_dcignoreFile(@NotNull Object file) {
    return pdUtils.getFileName(file).equals(".dcignore");
  }

  public boolean is_gitignoreFile(@NotNull Object file) {
    return pdUtils.getFileName(file).equals(".gitignore");
  }

  public void remove_ignoreFileContent(@NotNull Object ignoreFile) {
    removeIgnoredFilePaths(ignoreFile);
    map_ignore2PathMatchers.remove(ignoreFile);
    map_ignore2ReIncludePathMatchers.remove(ignoreFile);
  }

  public void removeProject(@NotNull Object project) {
    map_ignore2PathMatchers
      .keySet()
      .forEach(
        file -> {
          if (pdUtils.getProject(file).equals(project)) remove_ignoreFileContent(file);
        });
    map_ignore2ReIncludePathMatchers
      .keySet()
      .forEach(
        file -> {
          if (pdUtils.getProject(file).equals(project)) remove_ignoreFileContent(file);
        });
    project2IgnoredFilePaths.remove(project);
  }

  public void update_ignoreFileContent(@NotNull Object ignoreFile, @Nullable Object progress) {
    dcLogger.logInfo("Scanning .ignore file: " + pdUtils.getFilePath(ignoreFile));
    parse_ignoreFile2Globs(ignoreFile, progress);
    dcLogger.logInfo("Scan FINISHED for .ignore file: " + pdUtils.getFilePath(ignoreFile));
  }

  private void parse_ignoreFile2Globs(@NotNull Object ignoreFile, @Nullable Object progress) {
    pdUtils.progressSetText(progress, "parsing file: " + pdUtils.getFilePath(ignoreFile));
    Map<Integer, PathMatcher> ignoreMatchers = new HashMap<>();
    Map<Integer, PathMatcher> reIncludedMatchers = new HashMap<>();
    String basePath = pdUtils.getDirPath(ignoreFile);
    String lineSeparator = "\r\n|[\r\n]";
    final String fileText = hashContentUtils.doGetFileContent(ignoreFile);
    final String[] lines = fileText.split(lineSeparator);
    for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      String line = lines[lineIndex];

      // https://git-scm.com/docs/gitignore#_pattern_format
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;

      // An optional prefix "!" which negates the pattern;
      // any matching file excluded by a previous pattern will become included again.
      // todo??? It is not possible to re-include a file if a parent directory of that file is
      // excluded.
      boolean isReIncludePattern = line.startsWith("!");
      if (isReIncludePattern) line = line.substring(1);

      String prefix = basePath;
      // If there is a separator at the beginning or middle (or both) of the pattern, then the
      // pattern is relative to the directory level of the particular .gitignore file itself.
      // Otherwise the pattern may also match at any level below the .gitignore level.
      int indexBegMidSepar = line.substring(0, line.length() - 1).indexOf('/');
      if (indexBegMidSepar == -1) {
        prefix += "**/";
      } else if (indexBegMidSepar > 0) {
        if (line.endsWith("/*") || line.endsWith("/**")) {
          int indexLastSepar = line.lastIndexOf('/');
          if (indexBegMidSepar == indexLastSepar) prefix += "**/";
        } else {
          prefix += "/";
        }
      }

      // If there is a separator at the end of the pattern then the pattern will only match
      // directories, otherwise the pattern can match both files and directories.
      String postfix =
        (line.endsWith("/"))
          ? "?**" // should be dir
          : "{/?**,}"; // could be dir or file

      // glob sanity check for validity
      try {
        PathMatcher globToMatch =
          FileSystems.getDefault().getPathMatcher("glob:" + prefix + line + postfix);

        if (isReIncludePattern) {
          reIncludedMatchers.put(lineIndex, globToMatch);
        } else {
          ignoreMatchers.put(lineIndex, globToMatch);
        }
      } catch (PatternSyntaxException e) {
        dcLogger.logWarn("Incorrect Glob syntax in .ignore file: " + e.getMessage());
      }
      pdUtils.progressSetFraction(progress, (double) lineIndex / lines.length);
      pdUtils.progressCheckCanceled(progress);
    }
    map_ignore2ReIncludePathMatchers.put(ignoreFile, reIncludedMatchers);
    map_ignore2PathMatchers.put(ignoreFile, ignoreMatchers);
  }
}
