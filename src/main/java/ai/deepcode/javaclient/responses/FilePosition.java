package ai.deepcode.javaclient.responses;

import java.util.List;

public class FilePosition implements Position {

  private List<Integer> rows;
  private List<Integer> cols;
  private List<Marker> markers;

  public FilePosition(List<Integer> rows, List<Integer> cols, List<Marker> markers) {
    super();
    this.rows = rows;
    this.cols = cols;
    this.markers = markers;
  }

  @Override
  public List<Integer> getRows() {
    return rows;
  }

  @Override
  public List<Integer> getCols() {
    return cols;
  }

  public List<Marker> getMarkers() {
    return markers;
  }

  @Override
  public String toString() {
    return "suggestion range:  rows: " + rows + " cols: " + cols + " markers: " + markers;
  }
}
