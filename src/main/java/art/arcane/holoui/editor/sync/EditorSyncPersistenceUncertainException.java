package art.arcane.holoui.editor.sync;

import java.io.IOException;

final class EditorSyncPersistenceUncertainException extends IOException {
  EditorSyncPersistenceUncertainException(String message, IOException cause) {
    super(message, cause);
  }
}
