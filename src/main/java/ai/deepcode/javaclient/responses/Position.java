package ai.deepcode.javaclient.responses;

import java.util.List;

public interface Position {
  List<Integer> getRows();

  List<Integer> getCols();

  default String getFile() {
    return null;
  }

  @Override
  String toString();
}
