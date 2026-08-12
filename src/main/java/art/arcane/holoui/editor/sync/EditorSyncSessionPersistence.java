package art.arcane.holoui.editor.sync;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

interface EditorSyncSessionPersistence {
  Map<String, EditorSyncStoredSession> load(Instant now) throws IOException;

  void save(Iterable<EditorSyncStoredSession> sessions) throws IOException;

  void quarantine(EditorSyncStoredSession session, String reason);
}
