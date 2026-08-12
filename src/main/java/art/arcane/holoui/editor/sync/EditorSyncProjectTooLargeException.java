package art.arcane.holoui.editor.sync;

public final class EditorSyncProjectTooLargeException extends IllegalArgumentException {
  private final int actualBytes;
  private final int maximumBytes;

  public EditorSyncProjectTooLargeException(int actualBytes, int maximumBytes) {
    super("sync project is " + actualBytes + " bytes; maximum is " + maximumBytes);
    this.actualBytes = actualBytes;
    this.maximumBytes = maximumBytes;
  }

  public int actualBytes() {
    return actualBytes;
  }

  public int maximumBytes() {
    return maximumBytes;
  }
}
