package art.arcane.holoui.board;

import java.util.Map;

public record BoardLoadResult(int loaded, int retained, int removed, Map<String, String> failures) {
  public BoardLoadResult {
    if (loaded < 0 || retained < 0 || removed < 0) {
      throw new IllegalArgumentException("load counts must not be negative");
    }
    failures = Map.copyOf(failures);
  }

  public boolean successful() {
    return failures.isEmpty();
  }
}
