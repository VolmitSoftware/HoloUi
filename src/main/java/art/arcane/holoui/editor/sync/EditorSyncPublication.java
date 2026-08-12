package art.arcane.holoui.editor.sync;

import com.google.gson.JsonObject;

import java.util.Objects;

record EditorSyncPublication(long revision, String baseRevision, JsonObject snapshot) {
  EditorSyncPublication {
    if (revision < 1L) {
      throw new IllegalArgumentException("publication revision must be positive");
    }
    if (baseRevision == null || baseRevision.isBlank()) {
      throw new IllegalArgumentException("publication baseRevision must not be blank");
    }
    snapshot = Objects.requireNonNull(snapshot, "snapshot").deepCopy();
  }
}
