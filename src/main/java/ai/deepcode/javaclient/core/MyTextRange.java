package ai.deepcode.javaclient.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MyTextRange {
  private final int start;
  private final int end;
  private final int startRow;
  private final int endRow;
  private final int startCol;
  private final int endCol;
  //                msg range         poses in source file
  private final Map<MyTextRange, List<MyTextRange>> markers;
  private final String file;

  MyTextRange(
    int start,
    int end,
    int startRow,
    int endRow,
    int startCol,
    int endCol,
    Map<MyTextRange, List<MyTextRange>> markers,
    String file) {

    this.start = start;
    this.end = end;
    this.startRow = startRow;
    this.endRow = endRow;
    this.startCol = startCol;
    this.endCol = endCol;
    this.markers = markers;
    this.file = file;
  }

  MyTextRange(int start, int end) {
    this(start, end, -1, -1, -1, -1, Collections.emptyMap(), null);
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }

  public int getStartRow() {
    return startRow;
  }

  public int getEndRow() {
    return endRow;
  }

  public int getStartCol() {
    return startCol;
  }

  public int getEndCol() {
    return endCol;
  }

  public Map<MyTextRange, List<MyTextRange>> getMarkers() {
    return markers;
  }

  public String getFile() {
    return file;
  }
}
