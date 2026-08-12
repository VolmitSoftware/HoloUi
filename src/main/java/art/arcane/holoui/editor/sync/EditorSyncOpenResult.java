package art.arcane.holoui.editor.sync;

import java.time.Instant;
import java.util.Objects;

public record EditorSyncOpenResult(String sessionId, String editorUrl, String subjectId,
                                   EditorSyncKind kind, Instant expiresAt) {
  public EditorSyncOpenResult {
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("sessionId must not be blank");
    }
    if (editorUrl == null || editorUrl.isBlank()) {
      throw new IllegalArgumentException("editorUrl must not be blank");
    }
    if (subjectId == null || subjectId.isBlank()) {
      throw new IllegalArgumentException("subjectId must not be blank");
    }
    kind = Objects.requireNonNull(kind, "kind");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
  }
}
