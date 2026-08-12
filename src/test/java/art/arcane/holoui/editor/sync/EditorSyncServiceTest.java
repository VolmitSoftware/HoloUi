package art.arcane.holoui.editor.sync;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardTransform;
import art.arcane.holoui.config.HuiSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EditorSyncServiceTest {
  private static final Gson GSON = new GsonBuilder()
      .serializeNulls()
      .disableHtmlEscaping()
      .create();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();
  private final List<EditorSyncService> services = new ArrayList<>();

  @After
  public void stopServices() {
    for (EditorSyncService service : services) {
      if (service.isRunning()) {
        service.shutdown();
      }
    }
  }

  @Test
  public void editorUrlNormalizesTheBuilderAndKeepsRelayDataInTheFragment() {
    String endpoint = "https://relay.example/custom/v1";
    String url = EditorSyncService.editorUrl(
        "HTTPS://Editor.Example/path?discard=yes#old",
        endpoint, "session_id_123456789ab", "editor_token_123456789");
    String relay = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(endpoint.getBytes(StandardCharsets.UTF_8));

    assertEquals("https://editor.example/path/#/sync/session_id_123456789ab/"
        + "editor_token_123456789?relay=" + relay, url);
  }

  @Test
  public void boardCasBytesMustMatchTheSessionSnapshotSemantically() throws Exception {
    Path boardFile = temp.newFile("board.json").toPath();
    UUID boardUuid = UUID.randomUUID();
    UUID worldUuid = UUID.randomUUID();
    BoardDefinition captured = BoardDefinition.create("spawn/main", "menus/root",
        BoardTransform.at("minecraft:overworld", worldUuid, 1.0D, 2.0D, 3.0D, 0.0D));
    captured = new BoardDefinition(captured.schemaVersion(), captured.id(), boardUuid,
        captured.revision(), captured.rootMenuId(), captured.transform(), captured.follow(),
        captured.visibility());
    byte[] source = (GSON.toJson(captured) + System.lineSeparator())
        .getBytes(StandardCharsets.UTF_8);
    Files.write(boardFile, source);
    JsonObject project = new JsonObject();
    project.add("board", GSON.toJsonTree(captured));

    assertArrayEquals(source, EditorSyncService.expectedBoardFile(boardFile, project));

    BoardDefinition changed = captured.withTransform(BoardTransform.at("minecraft:overworld",
        worldUuid, 9.0D, 2.0D, 3.0D, 0.0D));
    Files.writeString(boardFile, GSON.toJson(changed), StandardCharsets.UTF_8);
    assertThrows(java.io.IOException.class,
        () -> EditorSyncService.expectedBoardFile(boardFile, project));
  }

  @Test
  public void activePollAtomicallyExcludesRevocationUntilItCompletes() throws Exception {
    EditorSyncStoredSession session = session(menuProject("{\"components\":[]}"), null);
    MemoryPersistence persistence = new MemoryPersistence(session);
    ControlledRelay relay = new ControlledRelay();
    CompletableFuture<Optional<EditorSyncPublication>> publication = new CompletableFuture<>();
    relay.publication.set(publication);
    EditorSyncService service = service(persistence, relay, new TestSettings(true), baseReader());

    service.start();
    CompletableFuture<Void> pull = service.pullNow(session.sessionId());
    assertTrue(relay.publicationStarted.await(1L, TimeUnit.SECONDS));
    CompletionException busy = assertThrows(CompletionException.class,
        () -> service.revoke(session.sessionId()).join());
    assertTrue(busy.getCause() instanceof IllegalStateException);

    publication.complete(Optional.empty());
    pull.join();
    service.revoke(session.sessionId()).join();
    assertEquals(1, relay.revocations.get());
  }

  @Test
  public void disabledSyncBlocksManualAndAutomaticPullButStillAllowsRevoke() {
    EditorSyncStoredSession session = session(menuProject("{\"components\":[]}"), null);
    ControlledRelay relay = new ControlledRelay();
    EditorSyncService service = service(new MemoryPersistence(session), relay,
        new TestSettings(false), baseReader());

    service.start();
    service.pollAllNow();
    assertEquals(0, relay.publications.get());
    assertThrows(CompletionException.class, () -> service.pullNow(session.sessionId()).join());
    service.revoke(session.sessionId()).join();
    assertEquals(1, relay.revocations.get());
  }

  @Test
  public void relayCapabilityIsRevokedWhenLocalSessionPersistenceFails() {
    MemoryPersistence persistence = new MemoryPersistence();
    persistence.failSaves.set(true);
    ControlledRelay relay = new ControlledRelay();
    EditorSyncService service = service(persistence, relay, new TestSettings(true), baseReader());
    service.start();

    assertThrows(CompletionException.class, () -> service.openMenu("fixture").join());

    assertEquals(1, relay.creates.get());
    assertEquals(1, relay.revocations.get());
    assertTrue(service.sessions().isEmpty());
  }

  @Test
  public void officialRelayWithoutCreateTokenFallsBackBeforeNetworkOrSnapshotWork() {
    TestSettings settings = new TestSettings(true);
    settings.endpoint = HuiSettings.EDITOR_SYNC_ENDPOINT_DEFAULT;
    ControlledRelay relay = new ControlledRelay();
    EditorSyncService service = service(new MemoryPersistence(), relay, settings, baseReader());
    service.start();

    CompletionException failure = assertThrows(CompletionException.class,
        () -> service.openMenu("fixture").join());

    assertTrue(failure.getCause().getMessage().contains("editorSyncCreateToken"));
    assertEquals(0, relay.creates.get());
  }

  @Test
  public void uncertainSessionStoreDurabilityPausesCreatesAndPullsUntilRestart() {
    MemoryPersistence persistence = new MemoryPersistence();
    persistence.uncertainSaves.set(true);
    ControlledRelay relay = new ControlledRelay();
    EditorSyncService service = service(persistence, relay, new TestSettings(true), baseReader());
    service.start();

    assertThrows(CompletionException.class, () -> service.openMenu("fixture").join());

    assertFalse(service.isAvailable());
    assertTrue(service.isRunning());
    assertEquals(1, relay.revocations.get());
    assertThrows(CompletionException.class, () -> service.openMenu("fixture").join());
    assertEquals(1, relay.creates.get());
  }

  @Test
  public void shutdownDrainsAnActiveRelayContinuationWithoutRejectingIt() throws Exception {
    EditorSyncStoredSession session = session(menuProject("{\"components\":[]}"), null);
    ControlledRelay relay = new ControlledRelay();
    CompletableFuture<Optional<EditorSyncPublication>> publication = new CompletableFuture<>();
    relay.publication.set(publication);
    EditorSyncService service = service(new MemoryPersistence(session), relay,
        new TestSettings(true), baseReader());
    service.start();
    CompletableFuture<Void> pull = service.pullNow(session.sessionId());
    assertTrue(relay.publicationStarted.await(1L, TimeUnit.SECONDS));

    Thread shutdown = new Thread(service::shutdown);
    shutdown.start();
    Thread.sleep(100L);
    assertTrue(shutdown.isAlive());
    publication.complete(Optional.empty());
    pull.join();
    shutdown.join(2000L);
    assertFalse(shutdown.isAlive());
  }

  @Test
  public void shutdownDrainDeadlineDoesNotInterruptUnfinishedPersistenceWork() {
    EditorSyncService service = service(new MemoryPersistence(), new ControlledRelay(),
        new TestSettings(true), baseReader());
    service.start();
    CompletableFuture<Void> persistenceWork = new CompletableFuture<>();
    service.track(persistenceWork);

    long started = System.nanoTime();
    service.shutdown(java.time.Duration.ofMillis(75L));
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

    assertTrue(elapsedMillis < 1000L);
    assertFalse(persistenceWork.isDone());
    persistenceWork.complete(null);
  }

  @Test(timeout = 3000L)
  public void shutdownDeadlineDoesNotWaitOnOrInterruptAnActiveSessionStoreWrite()
      throws Exception {
    MemoryPersistence persistence = new MemoryPersistence();
    persistence.blockSaves();
    EditorSyncService service = service(persistence, new ControlledRelay(),
        new TestSettings(true), baseReader());
    service.start();
    CompletableFuture<EditorSyncOpenResult> open = service.openMenu("fixture");
    assertTrue(persistence.saveStarted.await(1L, TimeUnit.SECONDS));

    try {
      long started = System.nanoTime();
      service.shutdown(java.time.Duration.ofMillis(75L));
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

      assertTrue(elapsedMillis < 1000L);
      assertFalse(open.isDone());
      assertFalse(persistence.saveInterrupted.get());
    } finally {
      persistence.releaseSaves.countDown();
    }
    open.join();
  }

  @Test
  public void shutdownCancelsAnOwningSchedulerPublicationBeforeWaiting() {
    EditorSyncService service = service(new MemoryPersistence(), new ControlledRelay(),
        new TestSettings(true), baseReader());
    service.start();
    CompletableFuture<Void> schedulerPublication = new CompletableFuture<>();
    CompletableFuture<Void> operation = schedulerPublication.handle((ignored, failure) -> null);
    service.trackSchedulerPublication(schedulerPublication);
    service.track(operation);

    service.shutdown();

    assertTrue(schedulerPublication.isCompletedExceptionally());
    assertTrue(operation.isDone());
  }

  @Test
  public void shutdownAlsoCancelsTheRollbackSchedulerPublication() {
    EditorSyncService service = service(new MemoryPersistence(), new ControlledRelay(),
        new TestSettings(true), baseReader());
    service.start();
    CompletableFuture<Void> rollbackPublication = new CompletableFuture<>();
    CompletableFuture<Void> operation = rollbackPublication.handle((ignored, failure) -> null);
    service.trackRollbackPublication(rollbackPublication);
    service.track(operation);

    service.shutdown();

    assertTrue(rollbackPublication.isCompletedExceptionally());
    assertTrue(operation.isDone());
  }

  @Test
  public void schedulerPublicationRegisteredAfterShutdownStartsIsCancelledImmediately()
      throws Exception {
    EditorSyncService service = service(new MemoryPersistence(), new ControlledRelay(),
        new TestSettings(true), baseReader());
    service.start();
    CompletableFuture<Void> latePublication = new CompletableFuture<>();
    CompletableFuture<Void> operation = latePublication.handle((ignored, failure) -> null);
    service.track(operation);

    Thread shutdown = new Thread(service::shutdown);
    shutdown.start();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
    while (service.isRunning() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertFalse(service.isRunning());
    service.trackSchedulerPublication(latePublication);

    shutdown.join(2000L);
    assertTrue(latePublication.isCompletedExceptionally());
    assertTrue(operation.isDone());
    assertFalse(shutdown.isAlive());
  }

  @Test
  public void pendingAcknowledgementRetriesAfterRestartWithoutReapplying() {
    JsonObject project = menuProject("{\"components\":[]}");
    EditorSyncPendingAck pending = new EditorSyncPendingAck(1L, "rejected", "Invalid edit.", null);
    EditorSyncStoredSession session = session(project, pending);
    MemoryPersistence persistence = new MemoryPersistence(session);
    EditorSyncService first = service(persistence, new ControlledRelay(),
        new TestSettings(true), baseReader());
    first.start();

    first.pullNow(session.sessionId()).join();
    first.shutdown();
    assertEquals(1L, persistence.sessions.get(session.sessionId()).lastPublicationRevision());
    assertNull(persistence.sessions.get(session.sessionId()).pendingAck());

    EditorSyncService restarted = service(persistence, new ControlledRelay(),
        new TestSettings(true), baseReader());
    restarted.start();
    assertEquals(1L, restarted.session(session.sessionId()).orElseThrow()
        .lastPublicationRevision());
  }

  @Test
  public void conflictAcknowledgementAdvancesToTheFreshServerSnapshot() {
    JsonObject base = menuProject("{\"components\":[]}");
    JsonObject editor = menuProject("{\"offset\":[0,0,1],\"components\":[]}");
    JsonObject server = menuProject("{\"offset\":[0,0,2],\"components\":[]}");
    EditorSyncStoredSession session = session(base, null);
    ControlledRelay relay = new ControlledRelay();
    relay.publication.set(CompletableFuture.completedFuture(Optional.of(
        new EditorSyncPublication(1L, session.baseRevision(), editor))));
    MemoryPersistence persistence = new MemoryPersistence(session);
    EditorSyncService service = service(persistence, relay, new TestSettings(true),
        (stored, scope, maximumBytes) -> EditorSyncProject.validated(server, maximumBytes));
    service.start();

    service.pullNow(session.sessionId()).join();

    assertEquals("conflict", relay.ackStatus.get());
    assertEquals(server.get("baseRevision").getAsString(),
        persistence.sessions.get(session.sessionId()).baseRevision());
    assertEquals(1L, persistence.sessions.get(session.sessionId()).lastPublicationRevision());
  }

  @Test
  public void rejectedPublicationAdvancesOnlyThePublicationRevision() {
    JsonObject base = menuProject("{\"components\":[]}");
    JsonObject invalid = menuProject("{\"components\":[]}");
    invalid.addProperty("unsupported", true);
    invalid.addProperty("baseRevision", EditorSyncJson.revision(invalid));
    EditorSyncStoredSession session = session(base, null);
    ControlledRelay relay = new ControlledRelay();
    relay.publication.set(CompletableFuture.completedFuture(Optional.of(
        new EditorSyncPublication(1L, session.baseRevision(), invalid))));
    MemoryPersistence persistence = new MemoryPersistence(session);
    EditorSyncService service = service(persistence, relay, new TestSettings(true), baseReader());
    service.start();

    service.pullNow(session.sessionId()).join();

    assertEquals("rejected", relay.ackStatus.get());
    assertEquals(session.baseRevision(), persistence.sessions.get(session.sessionId()).baseRevision());
    assertEquals(1L, persistence.sessions.get(session.sessionId()).lastPublicationRevision());
  }

  @Test
  public void malformedStoredBaseIsQuarantinedWithoutDisablingTheService() {
    JsonObject invalid = menuProject("{\"components\":[]}");
    invalid.addProperty("unsupported", true);
    invalid.addProperty("baseRevision", EditorSyncJson.revision(invalid));
    MemoryPersistence persistence = new MemoryPersistence(session(invalid, null));
    EditorSyncService service = service(persistence, new ControlledRelay(),
        new TestSettings(true), baseReader());

    service.start();

    assertTrue(service.sessions().isEmpty());
    assertEquals(1, persistence.quarantined.get());
    assertTrue(service.isRunning());
  }

  @Test
  public void malformedPendingServerProjectIsQuarantinedBeforePolling() {
    JsonObject base = menuProject("{\"components\":[]}");
    JsonObject invalidPending = base.deepCopy();
    invalidPending.addProperty("unsupported", true);
    invalidPending.addProperty("baseRevision", EditorSyncJson.revision(invalidPending));
    EditorSyncPendingAck pending = new EditorSyncPendingAck(
        1L, "applied", "Published.", invalidPending);
    MemoryPersistence persistence = new MemoryPersistence(session(base, pending));
    EditorSyncService service = service(persistence, new ControlledRelay(),
        new TestSettings(true), baseReader());

    service.start();

    assertTrue(service.sessions().isEmpty());
    assertEquals(1, persistence.quarantined.get());
    assertTrue(service.isRunning());
  }

  @Test
  public void createAdmissionStopsBeforeTheRelayAtThirtyTwoActiveSessions() {
    ControlledRelay relay = new ControlledRelay();
    EditorSyncService service = service(new MemoryPersistence(), relay,
        new TestSettings(true), baseReader());
    service.start();
    for (int index = 0; index < EditorSyncStorageLimits.MAX_ACTIVE_SESSIONS; index++) {
      service.openMenu("fixture").join();
    }

    CompletionException full = assertThrows(CompletionException.class,
        () -> service.openMenu("fixture").join());
    assertTrue(full.getCause() instanceof IllegalStateException);
    assertEquals(EditorSyncStorageLimits.MAX_ACTIVE_SESSIONS, relay.creates.get());
  }

  @Test
  public void normalizedMenuSourceNeverAppendsCrLfAfterExistingLf() {
    Map<String, String> normalized = EditorSyncService.normalizedMenus(
        Map.of("fixture", "{\"components\":[]}\n"));
    assertEquals("{\"components\":[]}\n", normalized.get("fixture"));
  }

  private EditorSyncService service(
      MemoryPersistence persistence, ControlledRelay relay, TestSettings settings,
      EditorSyncService.ScopedProjectReader reader) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    EditorSyncService service = new EditorSyncService(new EditorSyncService.Options(
        null, new FixedSnapshotSource(), new EditorSyncPublicationValidator(), relay,
        persistence, executor, logger(), settings, reader));
    services.add(service);
    return service;
  }

  private static EditorSyncService.ScopedProjectReader baseReader() {
    return (stored, scope, maximumBytes) -> EditorSyncProject.validated(scope, maximumBytes);
  }

  private static EditorSyncStoredSession session(JsonObject project,
                                                 EditorSyncPendingAck pending) {
    return new EditorSyncStoredSession(new EditorSyncStoredSession.Capability(
        "session_id_123456789ab", "server_token_123456789", "https://relay.example/v1"),
        new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "fixture"),
        Instant.now().plusSeconds(3600L), 0L, project, pending);
  }

  private static JsonObject menuProject(String source) {
    JsonObject project = new JsonObject();
    project.addProperty("format", EditorSyncJson.PROJECT_FORMAT);
    project.addProperty("version", 1);
    project.addProperty("kind", "menu");
    project.addProperty("subjectId", "fixture");
    JsonObject menu = new JsonObject();
    menu.addProperty("id", "fixture");
    menu.addProperty("json", source);
    JsonArray menus = new JsonArray();
    menus.add(menu);
    project.add("menus", menus);
    project.add("images", new JsonArray());
    JsonObject constraints = new JsonObject();
    constraints.addProperty("subjectId", "fixture");
    JsonArray menuIds = new JsonArray();
    menuIds.add("fixture");
    constraints.add("menuIds", menuIds);
    constraints.add("imagePaths", new JsonArray());
    constraints.addProperty("newImagePrefix", "sync/menus/fixture/");
    constraints.addProperty("allowDeletes", false);
    project.add("constraints", constraints);
    project.add("warnings", new JsonArray());
    project.addProperty("baseRevision", EditorSyncJson.revision(project));
    return project;
  }

  private static Logger logger() {
    Logger logger = Logger.getLogger(EditorSyncServiceTest.class.getName() + System.nanoTime());
    logger.setLevel(Level.OFF);
    return logger;
  }

  private static final class MemoryPersistence implements EditorSyncSessionPersistence {
    private final Map<String, EditorSyncStoredSession> sessions = new LinkedHashMap<>();
    private final AtomicInteger quarantined = new AtomicInteger();
    private final AtomicBoolean failSaves = new AtomicBoolean();
    private final AtomicBoolean uncertainSaves = new AtomicBoolean();
    private final AtomicBoolean saveInterrupted = new AtomicBoolean();
    private CountDownLatch saveStarted;
    private CountDownLatch releaseSaves;

    private MemoryPersistence(EditorSyncStoredSession... initial) {
      for (EditorSyncStoredSession session : initial) {
        sessions.put(session.sessionId(), session);
      }
    }

    @Override
    public synchronized Map<String, EditorSyncStoredSession> load(Instant now) {
      return Map.copyOf(sessions);
    }

    @Override
    public synchronized void save(Iterable<EditorSyncStoredSession> changed) throws IOException {
      if (saveStarted != null) {
        saveStarted.countDown();
        try {
          releaseSaves.await();
        } catch (InterruptedException interruption) {
          saveInterrupted.set(true);
          Thread.currentThread().interrupt();
          throw new IOException("injected session persistence interruption", interruption);
        }
      }
      if (uncertainSaves.get()) {
        throw new EditorSyncPersistenceUncertainException(
            "injected uncertain session persistence", new IOException("injected fsync failure"));
      }
      if (failSaves.get()) {
        throw new IllegalStateException("injected session persistence failure");
      }
      sessions.clear();
      for (EditorSyncStoredSession session : changed) {
        sessions.put(session.sessionId(), session);
      }
    }

    private void blockSaves() {
      saveStarted = new CountDownLatch(1);
      releaseSaves = new CountDownLatch(1);
    }

    @Override
    public void quarantine(EditorSyncStoredSession session, String reason) {
      quarantined.incrementAndGet();
    }
  }

  private static final class ControlledRelay implements EditorSyncRelayGateway {
    private final AtomicReference<CompletableFuture<Optional<EditorSyncPublication>>> publication =
        new AtomicReference<>(CompletableFuture.completedFuture(Optional.empty()));
    private final CountDownLatch publicationStarted = new CountDownLatch(1);
    private final AtomicInteger publications = new AtomicInteger();
    private final AtomicInteger creates = new AtomicInteger();
    private final AtomicInteger revocations = new AtomicInteger();
    private final AtomicReference<String> ackStatus = new AtomicReference<>();

    @Override
    public CompletableFuture<EditorSyncRelayClient.RelayCreated> create(
        String endpoint, String createToken, EditorSyncProject project, int expiresInSeconds) {
      int sequence = creates.incrementAndGet();
      return CompletableFuture.completedFuture(new EditorSyncRelayClient.RelayCreated(
          "session_" + String.format("%014d", sequence),
          "editor_token_" + String.format("%010d", sequence),
          "server_token_" + String.format("%010d", sequence),
          Instant.now().plusSeconds(expiresInSeconds)));
    }

    @Override
    public CompletableFuture<Optional<EditorSyncPublication>> publication(
        EditorSyncStoredSession session) {
      publications.incrementAndGet();
      publicationStarted.countDown();
      return publication.get();
    }

    @Override
    public CompletableFuture<Void> acknowledge(EditorSyncStoredSession session, long revision,
                                               String status, String message,
                                               EditorSyncProject serverProject) {
      ackStatus.set(status);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> revoke(EditorSyncStoredSession session) {
      revocations.incrementAndGet();
      return CompletableFuture.completedFuture(null);
    }
  }

  private static final class FixedSnapshotSource implements EditorSyncSnapshotSource {
    @Override
    public EditorSyncProject menu(String menuId, int maximumBytes) {
      return EditorSyncProject.validated(menuProject("{\"components\":[]}"), maximumBytes);
    }

    @Override
    public EditorSyncProject board(String boardId, int maximumBytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public EditorSyncProject fromContent(
        EditorSyncKind kind, String subjectId, BoardDefinition board,
        Map<String, String> menuSources, Map<String, byte[]> imageContents,
        JsonObject immutableConstraints, int maximumBytes) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class TestSettings implements EditorSyncSettingsView {
    private final AtomicBoolean enabled;
    private String endpoint;

    private TestSettings(boolean enabled) {
      this.enabled = new AtomicBoolean(enabled);
      this.endpoint = "https://relay.example/v1";
    }

    @Override
    public boolean enabled() {
      return enabled.get();
    }

    @Override
    public String endpoint() {
      return endpoint;
    }

    @Override
    public String createToken() {
      return "";
    }

    @Override
    public int sessionMinutes() {
      return 60;
    }

    @Override
    public int pollSeconds() {
      return 60;
    }

    @Override
    public int maximumProjectBytes() {
      return 8 * 1024 * 1024;
    }

    @Override
    public String builderUrl() {
      return "https://editor.example";
    }
  }
}
