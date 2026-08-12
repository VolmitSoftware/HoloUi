package art.arcane.holoui.editor.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EditorSyncSessionStoreTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void capabilitiesAndPendingAcknowledgementsSurviveRestart() throws Exception {
    Path data = temp.newFolder("restart").toPath();
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger());
    JsonObject project = fixtureProject();
    EditorSyncStoredSession session = new EditorSyncStoredSession(
        capability(), new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "fixture"),
        Instant.now().plusSeconds(3600L), 2L,
        project, new EditorSyncPendingAck(3L, "applied", "Published.", project));

    store.save(java.util.List.of(session));
    Map<String, EditorSyncStoredSession> loaded = store.load(Instant.now());

    EditorSyncStoredSession restored = loaded.get(session.sessionId());
    assertEquals(session.serverToken(), restored.serverToken());
    assertEquals(2L, restored.lastPublicationRevision());
    assertEquals(3L, restored.pendingAck().publicationRevision());
    assertEquals(EditorSyncJson.canonical(project),
        EditorSyncJson.canonical(restored.pendingAck().serverProject()));
  }

  @Test
  public void expiredAndMalformedEntriesAreIgnoredWithoutLosingValidSessions() throws Exception {
    Path data = temp.newFolder("mixed").toPath();
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger());
    JsonObject project = fixtureProject();
    EditorSyncStoredSession valid = new EditorSyncStoredSession(
        capability(), new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "fixture"),
        Instant.now().plusSeconds(3600L), 0L,
        project, null);
    store.save(java.util.List.of(valid));
    Path file = data.resolve("editor-sync-sessions.json");
    JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    JsonObject malformed = new JsonObject();
    malformed.addProperty("sessionId", "bad");
    root.getAsJsonArray("sessions").add(malformed);
    Files.writeString(file, root.toString());

    Map<String, EditorSyncStoredSession> loaded = store.load(Instant.now());
    assertEquals(java.util.Set.of(valid.sessionId()), loaded.keySet());

    store.save(loaded.values());
    JsonObject preserved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    assertEquals(2, preserved.getAsJsonArray("sessions").size());
  }

  @Test
  public void symbolicSessionStoreCannotRedirectCapabilityWrites() throws Exception {
    Path data = temp.newFolder("symlink-data").toPath();
    Path outside = temp.newFile("outside.json").toPath();
    Path storePath = data.resolve("editor-sync-sessions.json");
    try {
      Files.createSymbolicLink(storePath, outside);
    } catch (IOException | UnsupportedOperationException failure) {
      Assume.assumeNoException(failure);
    }
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger());

    assertThrows(IOException.class, () -> store.save(java.util.List.of()));
    assertTrue(Files.size(outside) == 0L);
  }

  @Test
  public void nonObjectOrUnknownVersionStoreFailsClosed() throws Exception {
    Path data = temp.newFolder("bad-root").toPath();
    Files.writeString(data.resolve("editor-sync-sessions.json"), "[]");
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger());
    assertThrows(IOException.class, () -> store.load(Instant.now()));
  }

  @Test
  public void oversizedStoreIsRejectedBeforeJsonAllocation() throws Exception {
    Path data = temp.newFolder("oversized-store").toPath();
    Path file = data.resolve("editor-sync-sessions.json");
    try (java.io.RandomAccessFile output = new java.io.RandomAccessFile(file.toFile(), "rw")) {
      output.setLength(EditorSyncStorageLimits.MAX_STORE_FILE_BYTES + 1L);
    }
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger());

    assertThrows(IOException.class, () -> store.load(Instant.now()));
  }

  @Test
  public void sessionStoreRetriesTheParentDirectoryFlushAfterAtomicRename() throws Exception {
    Path data = temp.newFolder("directory-flush").toPath();
    AtomicInteger attempts = new AtomicInteger();
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger(), directory -> {
      if (attempts.getAndIncrement() < 2) {
        throw new IOException("injected directory fsync failure");
      }
    });
    JsonObject project = fixtureProject();
    EditorSyncStoredSession session = new EditorSyncStoredSession(
        capability(), new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "fixture"),
        Instant.now().plusSeconds(3600L), 0L, project, null);

    store.save(List.of(session));

    assertEquals(3, attempts.get());
    assertEquals(session.sessionId(), store.load(Instant.now()).get(session.sessionId()).sessionId());
  }

  @Test
  public void persistentPostRenameFlushFailureIsReportedAsDurabilityUncertain()
      throws Exception {
    Path data = temp.newFolder("uncertain-directory-flush").toPath();
    EditorSyncSessionStore store = new EditorSyncSessionStore(data, logger(), directory -> {
      throw new IOException("injected persistent directory fsync failure");
    });
    JsonObject project = fixtureProject();
    EditorSyncStoredSession session = new EditorSyncStoredSession(
        capability(), new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "fixture"),
        Instant.now().plusSeconds(3600L), 0L, project, null);

    assertThrows(EditorSyncPersistenceUncertainException.class,
        () -> store.save(List.of(session)));

    assertTrue(Files.isRegularFile(data.resolve("editor-sync-sessions.json")));
  }

  private JsonObject fixtureProject() throws IOException {
    InputStream resource = getClass().getResourceAsStream("/editor-sync-canonical-v1.json");
    if (resource == null) {
      throw new IOException("missing canonical fixture");
    }
    try (InputStream input = resource;
         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("project");
    }
  }

  private EditorSyncStoredSession.Capability capability() {
    return new EditorSyncStoredSession.Capability("session_id_123456789ab",
        "server_token_123456789", "https://relay.example/v1");
  }

  private Logger logger() {
    Logger logger = Logger.getLogger(getClass().getName() + "." + System.nanoTime());
    logger.setLevel(Level.OFF);
    return logger;
  }
}
