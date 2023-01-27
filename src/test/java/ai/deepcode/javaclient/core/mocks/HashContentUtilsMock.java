package ai.deepcode.javaclient.core.mocks;

import ai.deepcode.javaclient.core.HashContentUtilsBase;
import ai.deepcode.javaclient.core.PlatformDependentUtilsBase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HashContentUtilsMock extends HashContentUtilsBase {

  public HashContentUtilsMock(@NotNull PlatformDependentUtilsBase platformDependentUtils) {
    super(platformDependentUtils);
  }

  @Override
  public @NotNull String doGetFileContent(@NotNull Object file) {
    try {
      return new String(Files.readAllBytes(Paths.get(((File) file).getAbsolutePath())));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
