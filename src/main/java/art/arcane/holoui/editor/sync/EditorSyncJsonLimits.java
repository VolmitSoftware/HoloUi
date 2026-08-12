package art.arcane.holoui.editor.sync;

import com.google.gson.JsonElement;

final class EditorSyncJsonLimits {
  private static final int MAX_DEPTH = 64;
  private static final int MAX_NODES = 200_000;
  private static final int MAX_STRING_CHARS = 2_000_000;
  private static final int MAX_PROPERTY_CHARS = 256;

  private EditorSyncJsonLimits() {
  }

  static void validate(JsonElement root) {
    Counter counter = new Counter();
    visit(root, 0, counter);
  }

  private static void visit(JsonElement value, int depth, Counter counter) {
    if (depth > MAX_DEPTH) {
      throw new IllegalArgumentException("sync project JSON nesting exceeds " + MAX_DEPTH);
    }
    counter.nodes++;
    if (counter.nodes > MAX_NODES) {
      throw new IllegalArgumentException("sync project JSON nodes exceed " + MAX_NODES);
    }
    if (value == null || value.isJsonNull()) {
      return;
    }
    if (value.isJsonPrimitive()) {
      if (value.getAsJsonPrimitive().isString()
          && value.getAsString().length() > MAX_STRING_CHARS) {
        throw new IllegalArgumentException("sync project string exceeds " + MAX_STRING_CHARS + " characters");
      }
      return;
    }
    if (value.isJsonArray()) {
      for (JsonElement child : value.getAsJsonArray()) {
        visit(child, depth + 1, counter);
      }
      return;
    }
    for (java.util.Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
      if (entry.getKey().length() > MAX_PROPERTY_CHARS) {
        throw new IllegalArgumentException("sync project property name is too long");
      }
      visit(entry.getValue(), depth + 1, counter);
    }
  }

  private static final class Counter {
    private int nodes;
  }
}
