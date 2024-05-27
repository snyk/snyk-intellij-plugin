package ai.deepcode.javaclient.responses;

import java.util.List;

public class Marker {

  private List<Integer> msg = null;
  private List<MarkerPosition> pos = null;

  public Marker(List<Integer> msg, List<MarkerPosition> pos) {
    super();
    this.msg = msg;
    this.pos = pos;
  }

  public List<Integer> getMsg() {
    return msg;
  }

  public List<MarkerPosition> getPos() {
    return pos;
  }

  @Override
  public String toString() {
    return " msg: " + msg + " pos: " + pos;
  }
}
