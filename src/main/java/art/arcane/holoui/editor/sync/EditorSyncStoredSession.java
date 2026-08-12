package art.arcane.holoui.editor.sync;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;

record EditorSyncStoredSession(Capability capability, Subject subject, Instant expiresAt,
                               long lastPublicationRevision, JsonObject baseProject,
                               EditorSyncPendingAck pendingAck) {
  EditorSyncStoredSession {
    capability = Objects.requireNonNull(capability, "capability");
    subject = Objects.requireNonNull(subject, "subject");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    if (lastPublicationRevision < 0L) {
      throw new IllegalArgumentException("lastPublicationRevision must not be negative");
    }
    baseProject = Objects.requireNonNull(baseProject, "baseProject").deepCopy();
  }

  String sessionId() {
    return capability.sessionId();
  }

  String serverToken() {
    return capability.serverToken();
  }

  String endpoint() {
    return capability.endpoint();
  }

  EditorSyncKind kind() {
    return subject.kind();
  }

  String subjectId() {
    return subject.subjectId();
  }

  @Override
  public JsonObject baseProject() {
    return baseProject.deepCopy();
  }

  String baseRevision() {
    return EditorSyncJson.requireString(baseProject, "baseRevision");
  }

  EditorSyncStoredSession withPendingAck(EditorSyncPendingAck acknowledgement) {
    return new EditorSyncStoredSession(capability, subject, expiresAt, lastPublicationRevision,
        baseProject, Objects.requireNonNull(acknowledgement));
  }

  EditorSyncStoredSession clearPendingAck() {
    return new EditorSyncStoredSession(capability, subject, expiresAt, lastPublicationRevision,
        baseProject, null);
  }

  EditorSyncStoredSession acknowledgePending() {
    if (pendingAck == null) {
      throw new IllegalStateException("session has no pending acknowledgement");
    }
    long revision = pendingAck.publicationRevision();
    if (revision <= lastPublicationRevision) {
      throw new IllegalArgumentException("publication revision must advance");
    }
    JsonObject project = pendingAck.serverProject() == null ? baseProject : pendingAck.serverProject();
    return new EditorSyncStoredSession(capability, subject, expiresAt, revision, project, null);
  }

  boolean expired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  static boolean validCapability(String value) {
    return value != null && value.length() >= 22 && value.length() <= 128
        && value.matches("[A-Za-z0-9_-]+");
  }

  @Override
  public String toString() {
    return "EditorSyncStoredSession[sessionId=" + sessionId() + ", kind=" + kind()
        + ", subjectId=" + subjectId() + ", expiresAt=" + expiresAt
        + ", lastPublicationRevision=" + lastPublicationRevision + "]";
  }

  record Capability(String sessionId, String serverToken, String endpoint) {
    Capability {
      if (!validCapability(sessionId)) {
        throw new IllegalArgumentException("invalid sync session id");
      }
      if (!validCapability(serverToken)) {
        throw new IllegalArgumentException("invalid sync server token");
      }
      if (!EditorSyncRelayClient.validEndpoint(endpoint)) {
        throw new IllegalArgumentException("invalid sync endpoint");
      }
    }
  }

  record Subject(EditorSyncKind kind, String subjectId) {
    Subject {
      kind = Objects.requireNonNull(kind, "kind");
      if (subjectId == null || subjectId.isBlank()) {
        throw new IllegalArgumentException("sync subjectId must not be blank");
      }
    }
  }
}
