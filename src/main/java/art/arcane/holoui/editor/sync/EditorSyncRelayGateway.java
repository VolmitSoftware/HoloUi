package art.arcane.holoui.editor.sync;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface EditorSyncRelayGateway {
  CompletableFuture<EditorSyncRelayClient.RelayCreated> create(
      String endpoint, String createToken, EditorSyncProject project, int expiresInSeconds);

  CompletableFuture<Optional<EditorSyncPublication>> publication(
      EditorSyncStoredSession session);

  CompletableFuture<Void> acknowledge(EditorSyncStoredSession session, long revision,
                                      String status, String message,
                                      EditorSyncProject serverProject);

  CompletableFuture<Void> revoke(EditorSyncStoredSession session);

  default void cancelActiveRequests() {
  }

  default void close() {
    cancelActiveRequests();
  }
}
