package ai.deepcode.javaclient.core;

import ai.deepcode.javaclient.core.mocks.DeepCodeIgnoreInfoHolderMock;
import ai.deepcode.javaclient.core.mocks.HashContentUtilsMock;
import ai.deepcode.javaclient.core.mocks.LoggerMock;
import ai.deepcode.javaclient.core.mocks.PlatformDependentUtilsMock;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeepCodeIgnoreInfoHolderTest {

  private final File basicIgnoreFile = new File("src/test/resources/basic/.dcignore");
  private final File basicProject = basicIgnoreFile.getParentFile();

  private final File fullDcignoreFile = new File("src/test/resources/full/.dcignore");
  private final File fullDcignoreProject = fullDcignoreFile.getParentFile();

  @Test
  public void ignoreFileNaming() {
    DeepCodeIgnoreInfoHolderBase ignoreInfoHolder = getNewIgnoreInfoHolder();
    assertTrue(ignoreInfoHolder.is_dcignoreFile(new File(basicProject, ".dcignore")));
    assertTrue(ignoreInfoHolder.is_gitignoreFile(new File(basicProject, ".gitignore")));
    assertTrue(ignoreInfoHolder.is_ignoreFile(new File(basicProject, ".dcignore")));
    assertTrue(ignoreInfoHolder.is_ignoreFile(new File(basicProject, "blabla/.dcignore")));
  }

  @Test
  public void basicIgnoreFile() {
    DeepCodeIgnoreInfoHolderBase ignoreInfoHolder = getNewIgnoreInfoHolder();
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(basicProject, "anyfile.js")));

    ignoreInfoHolder.update_ignoreFileContent(basicIgnoreFile, null);
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(basicProject, "2.js")));
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(basicProject, "1.js")));
  }

  @Test
  public void fullIgnoreFile() {
    DeepCodeIgnoreInfoHolderBase ignoreInfoHolder = getNewIgnoreInfoHolder();

    assertTrue(fullDcignoreFile.exists());
    ignoreInfoHolder.update_ignoreFileContent(fullDcignoreFile, null);

    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "1.js")));
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "scripts/1.js")));
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "node_modules/1.js")));
    assertTrue(
      ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "node_modules/1/1/1.js")));

    // # Hidden directories
    // .*/
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "1/.1/1.js")));

    // # Godot
    // data_*/
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "1/data_1/1.js")));

    // # Lilypond
    // *~
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "1/1~/1.js")));

    // # Python
    // /site
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "site/1.js")));
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "1/site/1.js")));

    // # Unity
    // /[Ll]ibrary/
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "library/1.js")));
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "Kibrary/1.js")));

    // # VisualStudio
    // ~$*
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "~$1/1.js")));

    // # Emacs
    // \#*\#
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "#1#")));

    // # Magento1
    // /media/*
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "media/1.js")));
    // !/media/dhl
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "media/dhl")));
    // /media/dhl/*
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "media/dhl/1.js")));

    // # IAR_EWARM
    // EWARM/**/Obj
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(fullDcignoreProject, "EWARM/1/1/Obj/1.js")));
  }

  @Test
  public void removeIgnoreFile() {
    DeepCodeIgnoreInfoHolderBase ignoreInfoHolder = getNewIgnoreInfoHolder();
    ignoreInfoHolder.update_ignoreFileContent(basicIgnoreFile, null);

    ignoreInfoHolder.remove_ignoreFileContent(basicIgnoreFile);
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(basicProject, "2.js")));
  }

  @Test
  public void removeProject() {
    DeepCodeIgnoreInfoHolderBase ignoreInfoHolder = getNewIgnoreInfoHolder();
    ignoreInfoHolder.update_ignoreFileContent(basicIgnoreFile, null);

    ignoreInfoHolder.removeProject(basicProject);
    assertFalse(ignoreInfoHolder.isIgnoredFile(new File(basicProject, "2.js")));
  }

  @Test
  public void scanAllMissedIgnoreFiles() {
    DeepCodeIgnoreInfoHolderBase ignoreInfoHolder = getNewIgnoreInfoHolder();

    ignoreInfoHolder.scanAllMissedIgnoreFiles(Collections.singletonList(basicIgnoreFile), null);
    assertTrue(ignoreInfoHolder.isIgnoredFile(new File(basicProject, "2.js")));
  }

  private PlatformDependentUtilsBase pdUtils =
    new PlatformDependentUtilsMock() {
      @Override
      public @NotNull Object getProject(@NotNull Object file) {
        final String filePath = ((File) file).getPath();
        if (filePath.startsWith(basicProject.getPath())) return basicProject;
        if (filePath.startsWith(fullDcignoreProject.getPath())) return fullDcignoreProject;
        throw new IllegalArgumentException(file.toString());
      }

      @Override
      public @NotNull String getProjectName(@NotNull Object project) {
        return project.toString();
      }

      @Override
      public @NotNull String getFileName(@NotNull Object file) {
        return ((File) file).getName();
      }

      @Override
      public @NotNull String getFilePath(@NotNull Object file) {
        return ((File) file).getPath().replaceAll("\\\\", "/"); // case for Windows base path
      }

      @Override
      public @NotNull String getDirPath(@NotNull Object file) {
        return ((File) file).getParent().replaceAll("\\\\", "/"); // case for Windows base path
      }
    };

  @NotNull
  private DeepCodeIgnoreInfoHolderBase getNewIgnoreInfoHolder() {
    return new DeepCodeIgnoreInfoHolderMock(
      new HashContentUtilsMock(pdUtils), pdUtils, new LoggerMock());
  }
}
