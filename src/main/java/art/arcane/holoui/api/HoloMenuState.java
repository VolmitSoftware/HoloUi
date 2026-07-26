package art.arcane.holoui.api;

public enum HoloMenuState {
  PENDING,
  OPEN,
  CLOSED,
  FAILED;

  public boolean terminal() {
    return this == CLOSED || this == FAILED;
  }
}
