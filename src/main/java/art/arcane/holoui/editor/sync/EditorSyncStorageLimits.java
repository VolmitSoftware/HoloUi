package art.arcane.holoui.editor.sync;

import java.nio.charset.StandardCharsets;

final class EditorSyncStorageLimits {
  static final int MAX_ACTIVE_SESSIONS = 32;
  static final long MAX_PROJECT_SNAPSHOT_BYTES = 64L * 1024L * 1024L;
  static final long MAX_STORE_FILE_BYTES = 80L * 1024L * 1024L;

  private EditorSyncStorageLimits() {
  }

  static long bytes(EditorSyncStoredSession session) {
    long total = EditorSyncJson.canonical(session.baseProject())
        .getBytes(StandardCharsets.UTF_8).length;
    EditorSyncPendingAck pending = session.pendingAck();
    if (pending != null && pending.serverProject() != null) {
      total += EditorSyncJson.canonical(pending.serverProject())
          .getBytes(StandardCharsets.UTF_8).length;
    }
    return total;
  }
}
