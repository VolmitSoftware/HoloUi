package art.arcane.holoui.editor.sync;

import java.time.Instant;
import java.util.Objects;

public record EditorSyncSessionInfo(String sessionId, EditorSyncKind kind, String subjectId,
                                    Instant expiresAt, long lastPublicationRevision,
                                    String pendingStatus) {
  public EditorSyncSessionInfo {
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("sessionId must not be blank");
    }
    kind = Objects.requireNonNull(kind, "kind");
    if (subjectId == null || subjectId.isBlank()) {
      throw new IllegalArgumentException("subjectId must not be blank");
    }
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
  }

  static EditorSyncSessionInfo from(EditorSyncStoredSession session) {
    EditorSyncPendingAck pending = session.pendingAck();
    return new EditorSyncSessionInfo(session.sessionId(), session.kind(), session.subjectId(),
        session.expiresAt(), session.lastPublicationRevision(),
        pending == null ? null : pending.status());
  }
}
