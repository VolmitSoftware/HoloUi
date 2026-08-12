package art.arcane.holoui.editor.sync;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.menu.MenuDocument;
import art.arcane.holoui.editor.EditorMenuHandoff;
import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import art.arcane.holoui.persistence.HoloUiProjectTransaction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class EditorSyncService {
  private static final Gson GSON = new GsonBuilder()
      .serializeNulls()
      .disableHtmlEscaping()
      .create();
  private static final Duration SHUTDOWN_DRAIN_TIMEOUT = Duration.ofSeconds(30L);
  private static final Duration EXECUTOR_TERMINATION_TIMEOUT = Duration.ofSeconds(10L);

  private final HoloUI plugin;
  private final EditorSyncSnapshotSource snapshotBuilder;
  private final EditorSyncPublicationValidator publicationValidator;
  private final EditorSyncRelayGateway relay;
  private final EditorSyncSessionPersistence store;
  private final ScheduledExecutorService executor;
  private final java.util.logging.Logger logger;
  private final EditorSyncSettingsView settings;
  private final ScopedProjectReader scopedProjectReader;
  private final Map<String, EditorSyncStoredSession> sessions;
  private final Map<String, EditorSyncPollBackoff> pollBackoffs;
  private final Set<String> inFlight;
  private final Set<String> recoveryRequired;
  private final Set<CompletableFuture<?>> activeOperations;
  private final Set<CompletableFuture<?>> schedulerPublications;
  private final Object sessionLock;
  private final Object lifecycleLock;
  private final AtomicBoolean running;
  private final AtomicBoolean persistenceHealthy;
  private int reservedCreates;
  private long reservedCreateBytes;

  public EditorSyncService(HoloUI plugin) {
    this(productionOptions(Objects.requireNonNull(plugin, "plugin")));
  }

  EditorSyncService(Options options) {
    Options configured = Objects.requireNonNull(options, "options");
    this.plugin = configured.plugin();
    this.snapshotBuilder = Objects.requireNonNull(configured.snapshotSource(), "snapshotSource");
    this.publicationValidator = Objects.requireNonNull(configured.publicationValidator(),
        "publicationValidator");
    this.relay = Objects.requireNonNull(configured.relay(), "relay");
    this.store = Objects.requireNonNull(configured.store(), "store");
    this.executor = Objects.requireNonNull(configured.executor(), "executor");
    this.logger = Objects.requireNonNull(configured.logger(), "logger");
    this.settings = Objects.requireNonNull(configured.settings(), "settings");
    this.scopedProjectReader = Objects.requireNonNull(configured.scopedProjectReader(),
        "scopedProjectReader");
    this.sessions = new ConcurrentHashMap<>();
    this.pollBackoffs = new ConcurrentHashMap<>();
    this.inFlight = ConcurrentHashMap.newKeySet();
    this.recoveryRequired = ConcurrentHashMap.newKeySet();
    this.activeOperations = ConcurrentHashMap.newKeySet();
    this.schedulerPublications = ConcurrentHashMap.newKeySet();
    this.sessionLock = new Object();
    this.lifecycleLock = new Object();
    this.running = new AtomicBoolean();
    this.persistenceHealthy = new AtomicBoolean(true);
  }

  private static Options productionOptions(HoloUI plugin) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "holoui-editor-sync");
      thread.setDaemon(true);
      thread.setContextClassLoader(plugin.getClass().getClassLoader());
      return thread;
    });
    ScheduledExecutorService deadlineExecutor =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable, "holoui-editor-sync-deadline");
          thread.setDaemon(true);
          thread.setContextClassLoader(plugin.getClass().getClassLoader());
          return thread;
        });
    EditorSyncSettingsView settings = EditorSyncSettingsView.runtime();
    HttpClient http = EditorSyncRelayClient.productionHttpClient(executor);
    EditorSyncSnapshotBuilder snapshots = new EditorSyncSnapshotBuilder(plugin);
    return new Options(plugin, snapshots,
        new EditorSyncPublicationValidator(),
        new EditorSyncRelayClient(http, deadlineExecutor, settings::maximumProjectBytes),
        new EditorSyncSessionStore(plugin.getDataFolder().toPath(), plugin.getLogger()),
        executor, plugin.getLogger(), settings,
        (session, scope, maximumBytes) -> scopedProject(plugin, snapshots, session, scope,
            maximumBytes));
  }

  public void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      Map<String, EditorSyncStoredSession> loaded = store.load(Instant.now());
      for (EditorSyncStoredSession session : loaded.values()) {
        try {
          validateStoredSession(session);
          if (sessions.size() >= EditorSyncStorageLimits.MAX_ACTIVE_SESSIONS) {
            throw new IllegalArgumentException("stored editor sync sessions exceed the active limit");
          }
          requireStoredCapacity(null, session);
          sessions.put(session.sessionId(), session);
        } catch (RuntimeException failure) {
          store.quarantine(session, safeMessage(failure));
          logger.log(Level.WARNING,
              "Stored editor sync session " + abbreviate(session.sessionId())
                  + " was quarantined and will not be polled.", failure);
        }
      }
    } catch (IOException | RuntimeException failure) {
      running.set(false);
      relay.close();
      executor.shutdownNow();
      throw new IllegalStateException("Unable to load editor sync sessions", failure);
    }
    long period = settings.pollSeconds();
    executor.scheduleWithFixedDelay(this::pollAllSafely, period, period, TimeUnit.SECONDS);
  }

  public CompletableFuture<EditorSyncOpenResult> openMenu(String menuId) {
    return create(EditorSyncKind.MENU, menuId);
  }

  public boolean isRunning() {
    return running.get();
  }

  public boolean isAvailable() {
    return running.get() && persistenceHealthy.get();
  }

  public CompletableFuture<EditorSyncOpenResult> openBoard(String boardId) {
    return create(EditorSyncKind.BOARD, boardId);
  }

  public List<EditorSyncSessionInfo> sessions() {
    List<EditorSyncSessionInfo> result = new ArrayList<>();
    synchronized (sessionLock) {
      for (EditorSyncStoredSession session : sessions.values()) {
        result.add(EditorSyncSessionInfo.from(session));
      }
    }
    result.sort(Comparator.comparing(EditorSyncSessionInfo::expiresAt)
        .thenComparing(EditorSyncSessionInfo::sessionId));
    return List.copyOf(result);
  }

  public Optional<EditorSyncSessionInfo> session(String sessionId) {
    EditorSyncStoredSession session = sessions.get(sessionId);
    return session == null ? Optional.empty() : Optional.of(EditorSyncSessionInfo.from(session));
  }

  public CompletableFuture<Void> pullNow(String sessionId) {
    requireRunning();
    if (!persistenceHealthy.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("editor sync persistence requires restart recovery"));
    }
    if (!settings.enabled()) {
      return CompletableFuture.failedFuture(new IllegalStateException("editor sync is disabled"));
    }
    if (!sessions.containsKey(sessionId)) {
      return CompletableFuture.failedFuture(new NoSuchElementException("unknown sync session"));
    }
    return poll(sessionId, true);
  }

  public CompletableFuture<Void> revoke(String sessionId) {
    requireRunning();
    EditorSyncStoredSession session = sessions.get(sessionId);
    if (session == null) {
      return CompletableFuture.failedFuture(new NoSuchElementException("unknown sync session"));
    }
    if (!inFlight.add(sessionId)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("sync session is processing a publication"));
    }
    CompletableFuture<Void> revocation = startTracked(() -> relay.revoke(session)
        .thenRunAsync(() -> {
          synchronized (sessionLock) {
            sessions.remove(sessionId, session);
            saveSessions();
          }
        }, executor));
    revocation.whenComplete((ignored, failure) -> inFlight.remove(sessionId));
    return revocation;
  }

  public void shutdown() {
    shutdown(SHUTDOWN_DRAIN_TIMEOUT);
  }

  void shutdown(Duration drainTimeout) {
    Duration timeout = Objects.requireNonNull(drainTimeout, "drainTimeout");
    if (timeout.isNegative()) {
      throw new IllegalArgumentException("drainTimeout must not be negative");
    }
    if (!running.compareAndSet(true, false)) {
      return;
    }
    relay.close();
    CancellationException shutdownCancellation =
        new CancellationException("editor sync is shutting down");
    synchronized (lifecycleLock) {
      for (CompletableFuture<?> publication : List.copyOf(schedulerPublications)) {
        publication.completeExceptionally(shutdownCancellation);
      }
    }
    boolean interrupted = false;
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!activeOperations.isEmpty()) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0L) {
        break;
      }
      CompletableFuture<?>[] active = activeOperations.toArray(CompletableFuture[]::new);
      try {
        CompletableFuture.allOf(active).get(remaining, TimeUnit.NANOSECONDS);
      } catch (InterruptedException interruption) {
        interrupted = true;
        break;
      } catch (java.util.concurrent.ExecutionException failure) {
        logger.log(Level.WARNING,
            "An editor sync operation failed while shutdown was draining it.", rootCause(failure));
      } catch (java.util.concurrent.TimeoutException drainExpired) {
        break;
      }
    }
    if (activeOperations.isEmpty()) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT.toNanos(),
            TimeUnit.NANOSECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException interruption) {
        interrupted = true;
        executor.shutdownNow();
      }
    } else {
      CompletableFuture<?>[] remaining = activeOperations.toArray(CompletableFuture[]::new);
      logger.log(Level.SEVERE,
          "Editor sync shutdown reached its drain deadline with " + remaining.length
              + " operation(s) still active; persistence work will finish without interruption.");
      CompletableFuture.allOf(remaining).whenComplete((ignored, failure) -> executor.shutdown());
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private CompletableFuture<EditorSyncOpenResult> create(EditorSyncKind kind, String subjectId) {
    requireRunning();
    if (!persistenceHealthy.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("editor sync persistence requires restart recovery"));
    }
    if (!settings.enabled()) {
      return CompletableFuture.failedFuture(new IllegalStateException("editor sync is disabled"));
    }
    String endpoint = settings.endpoint();
    String createToken = settings.createToken();
    if (HuiSettings.EDITOR_SYNC_ENDPOINT_DEFAULT.equals(endpoint) && createToken.isBlank()) {
      return CompletableFuture.failedFuture(new IllegalStateException(
          "the official editor sync relay requires editorSyncCreateToken"));
    }
    return startTracked(() ->
        CompletableFuture.supplyAsync(() -> currentProject(kind, subjectId), executor)
            .thenCompose(project -> {
              reserveCreate(project);
              return relay.create(endpoint, createToken, project,
                  settings.sessionMinutes() * 60)
                  .thenCompose(created -> persistCreated(project, created, endpoint))
                  .whenComplete((ignored, failure) -> releaseCreate(project));
            }));
  }

  private CompletableFuture<EditorSyncOpenResult> persistCreated(
      EditorSyncProject project, EditorSyncRelayClient.RelayCreated created, String endpoint) {
    EditorSyncStoredSession candidate = createdSession(project, created, endpoint);
    return CompletableFuture.supplyAsync(() -> storeCreated(candidate, created), executor)
        .exceptionallyCompose(failure -> revokeFailedCreate(candidate, failure));
  }

  private CompletableFuture<EditorSyncOpenResult> revokeFailedCreate(
      EditorSyncStoredSession candidate, Throwable originalFailure) {
    Throwable cause = rootCause(originalFailure);
    logger.log(Level.WARNING,
        "Unable to persist newly created editor sync session "
            + abbreviate(candidate.sessionId()) + "; revoking its relay capability.", cause);
    return relay.revoke(candidate).handle((ignored, revokeFailure) -> {
      if (revokeFailure != null) {
        Throwable revokeCause = rootCause(revokeFailure);
        cause.addSuppressed(revokeCause);
        logger.log(Level.SEVERE,
            "Unable to revoke orphaned editor sync session "
                + abbreviate(candidate.sessionId()) + ".", revokeCause);
      }
      throw new CompletionException(cause);
    });
  }

  private EditorSyncStoredSession createdSession(
      EditorSyncProject project, EditorSyncRelayClient.RelayCreated created, String endpoint) {
    return new EditorSyncStoredSession(
        new EditorSyncStoredSession.Capability(created.sessionId(), created.serverToken(),
            endpoint),
        new EditorSyncStoredSession.Subject(project.kind(), project.subjectId()),
        created.expiresAt(), 0L, project.json(), null);
  }

  private EditorSyncOpenResult storeCreated(EditorSyncStoredSession session,
                                            EditorSyncRelayClient.RelayCreated created) {
    synchronized (sessionLock) {
      if (!running.get()) {
        throw new CancellationException("editor sync is shutting down");
      }
      sessions.put(session.sessionId(), session);
      try {
        requireStoredCapacity(null, session);
        saveSessions();
      } catch (RuntimeException failure) {
        sessions.remove(session.sessionId(), session);
        throw failure;
      }
    }
    String editorUrl = editorUrl(settings.builderUrl(), session.endpoint(),
        session.sessionId(), created.editorToken());
    return new EditorSyncOpenResult(session.sessionId(), editorUrl, session.subjectId(),
        session.kind(), session.expiresAt());
  }

  private void pollAllSafely() {
    if (!running.get() || !persistenceHealthy.get() || !settings.enabled()) {
      return;
    }
    Instant now = Instant.now();
    for (EditorSyncStoredSession session : List.copyOf(sessions.values())) {
      if (session.expired(now)) {
        expire(session);
      } else if (backoff(session.sessionId()).ready(now)) {
        poll(session.sessionId(), false).whenComplete((ignored, failure) -> {
          if (failure == null) {
            pollBackoffs.remove(session.sessionId());
            return;
          }
          Throwable cause = rootCause(failure);
          if (cause instanceof CancellationException) {
            pollBackoffs.remove(session.sessionId());
          } else {
            Duration delay = backoff(session.sessionId()).fail(session.sessionId(),
                settings.pollSeconds(), Instant.now());
            logger.log(Level.WARNING,
                "Editor sync poll failed for session " + abbreviate(session.sessionId())
                    + " (" + session.kind().wireName() + " " + session.subjectId()
                    + "); retrying in " + delay.toSeconds() + " seconds.",
                cause);
          }
        });
      }
    }
  }

  void pollAllNow() {
    pollAllSafely();
  }

  private CompletableFuture<Void> poll(String sessionId, boolean reportBusy) {
    if (!persistenceHealthy.get()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("editor sync persistence requires restart recovery"));
    }
    if (!settings.enabled()) {
      return CompletableFuture.failedFuture(new IllegalStateException("editor sync is disabled"));
    }
    if (recoveryRequired.contains(sessionId)) {
      return reportBusy
          ? CompletableFuture.failedFuture(new IllegalStateException(
              "sync session requires server restart recovery before it can be polled"))
          : CompletableFuture.completedFuture(null);
    }
    if (!inFlight.add(sessionId)) {
      return reportBusy
          ? CompletableFuture.failedFuture(
              new IllegalStateException("sync session is already being polled"))
          : CompletableFuture.completedFuture(null);
    }
    EditorSyncStoredSession session = sessions.get(sessionId);
    if (session == null || !running.get()) {
      inFlight.remove(sessionId);
      return CompletableFuture.failedFuture(new CancellationException("sync session is unavailable"));
    }
    CompletableFuture<Void> work = startTracked(() -> session.pendingAck() == null
        ? fetchPublication(session)
        : reconcileAndAcknowledge(session));
    work.whenComplete((ignored, failure) -> inFlight.remove(sessionId));
    return work;
  }

  <T> CompletableFuture<T> track(CompletableFuture<T> future) {
    synchronized (lifecycleLock) {
      if (!running.get()) {
        future.completeExceptionally(
            new CancellationException("editor sync is shutting down"));
        return future;
      }
      activeOperations.add(future);
    }
    future.whenComplete((ignored, failure) -> activeOperations.remove(future));
    return future;
  }

  private <T> CompletableFuture<T> startTracked(
      Supplier<CompletableFuture<T>> operationStarter) {
    CompletableFuture<T> tracked = new CompletableFuture<>();
    synchronized (lifecycleLock) {
      if (!running.get()) {
        tracked.completeExceptionally(
            new CancellationException("editor sync is shutting down"));
        return tracked;
      }
      activeOperations.add(tracked);
    }
    tracked.whenComplete((ignored, failure) -> activeOperations.remove(tracked));
    try {
      CompletableFuture<T> operation =
          Objects.requireNonNull(operationStarter.get(), "operationStarter result");
      operation.whenComplete((result, failure) -> {
        if (failure == null) {
          tracked.complete(result);
        } else {
          tracked.completeExceptionally(failure);
        }
      });
    } catch (RuntimeException failure) {
      tracked.completeExceptionally(failure);
    }
    return tracked;
  }

  private CompletableFuture<Void> fetchPublication(EditorSyncStoredSession session) {
    return relay.publication(session).thenComposeAsync(publication -> {
      if (publication.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      return processPublication(currentSession(session.sessionId()), publication.get());
    }, executor);
  }

  private CompletableFuture<Void> reconcileAndAcknowledge(EditorSyncStoredSession session) {
    EditorSyncPendingAck acknowledgement = session.pendingAck();
    if (acknowledgement == null) {
      return fetchPublication(session);
    }
    if (acknowledgement.serverProject() != null) {
      EditorSyncProject expected = acknowledgement.validatedServerProject(
          settings.maximumProjectBytes());
      EditorSyncProject current;
      try {
        current = currentProject(session, expected.json());
      } catch (RuntimeException failure) {
        clearPending(session);
        return fetchPublication(currentSession(session.sessionId()));
      }
      if (!current.baseRevision().equals(expected.baseRevision())) {
        clearPending(session);
        return fetchPublication(currentSession(session.sessionId()));
      }
    }
    return sendPending(session);
  }

  private CompletableFuture<Void> processPublication(EditorSyncStoredSession session,
                                                     EditorSyncPublication publication) {
    EditorSyncProject current;
    try {
      current = currentProject(session, session.baseProject());
    } catch (RuntimeException failure) {
      return queueAndSend(session, new EditorSyncPendingAck(publication.revision(), "rejected",
          "The server subject no longer exists or cannot be read.", null));
    }
    if (!current.baseRevision().equals(session.baseRevision())) {
      return queueAndSend(session, new EditorSyncPendingAck(publication.revision(), "conflict",
          "The server project changed after this editor session was opened.", current.json()));
    }

    EditorSyncPublicationValidator.ValidatedProject validated;
    try {
      validated = publicationValidator.validate(session, publication,
          settings.maximumProjectBytes());
    } catch (RuntimeException failure) {
      return queueAndSend(session, new EditorSyncPendingAck(publication.revision(), "rejected",
          safeMessage(failure), null));
    }
    return applyPublication(session, publication, validated);
  }

  private CompletableFuture<Void> applyPublication(
      EditorSyncStoredSession session, EditorSyncPublication publication,
      EditorSyncPublicationValidator.ValidatedProject validated) {
    BoardDefinition previousBoard = session.kind() == EditorSyncKind.BOARD
        ? plugin.getBoardService().get(session.subjectId()).orElse(null)
        : null;
    if (session.kind() == EditorSyncKind.BOARD && previousBoard == null) {
      return queueAndSend(session, new EditorSyncPendingAck(publication.revision(), "rejected",
          "The board no longer exists.", null));
    }
    BoardDefinition appliedBoard = nextBoard(previousBoard, validated.board());
    Map<String, String> menus = normalizedMenus(validated.menus());
    EditorSyncProject expectedApplied = snapshotBuilder.fromContent(session.kind(), session.subjectId(),
        appliedBoard, menus, validated.images(),
        EditorSyncJson.requireObject(session.baseProject(), "constraints"),
        settings.maximumProjectBytes());
    EditorSyncPendingAck acknowledgement = new EditorSyncPendingAck(publication.revision(), "applied",
        "Published to the server.", expectedApplied.json());
    EditorSyncStoredSession pendingSession = storePending(session, acknowledgement);

    HoloUiPersistenceCoordinator.ExternalTransaction lease;
    try {
      lease = plugin.getPersistenceCoordinator().beginExternalTransaction();
    } catch (InterruptedException interruption) {
      Thread.currentThread().interrupt();
      clearPending(pendingSession);
      return CompletableFuture.failedFuture(new CancellationException("editor sync apply was interrupted"));
    }

    EditorSyncProject lockedCurrent;
    try {
      lockedCurrent = currentProject(session, session.baseProject());
      if (!lockedCurrent.baseRevision().equals(session.baseRevision())) {
        clearPending(pendingSession);
        lease.close();
        return queueAndSend(currentSession(session.sessionId()),
            new EditorSyncPendingAck(publication.revision(), "conflict",
                "The server project changed while the publication was being prepared.",
                lockedCurrent.json()));
      }
    } catch (RuntimeException failure) {
      clearPending(pendingSession);
      lease.close();
      return CompletableFuture.failedFuture(failure);
    }

    HoloUiProjectTransaction.Pending transaction;
    try {
      Map<Path, byte[]> expectedFiles = expectedFiles(session, validated);
      transaction = plugin.getProjectTransaction().apply(session.sessionId(), menus,
          validated.images(), appliedBoard, expectedFiles);
    } catch (IOException | RuntimeException failure) {
      clearPending(pendingSession);
      lease.close();
      return CompletableFuture.failedFuture(failure);
    }

    CompletableFuture<List<MenuDocument>> menuPublication =
        plugin.getConfigManager().publishEditorSyncProject(menus);
    trackSchedulerPublication(menuPublication);
    CompletableFuture<BoardDefinition> published = menuPublication.thenApply(documents -> {
          if (appliedBoard == null) {
            return null;
          }
          try {
            return plugin.getBoardService().publishExternalUpdate(previousBoard, appliedBoard);
          } catch (IOException failure) {
            throw new CompletionException(failure);
          }
        });
    CompletableFuture<Void> result = new CompletableFuture<>();
    published.whenCompleteAsync((ignored, failure) -> {
      if (failure != null) {
        rollbackPublication(session, pendingSession, transaction, previousBoard, appliedBoard,
            lease, failure, result);
        return;
      }
      EditorSyncStoredSession actualPendingSession = pendingSession;
      try {
        EditorSyncProject actualProject = currentProject(session, expectedApplied.json());
        actualPendingSession = storePending(pendingSession,
            new EditorSyncPendingAck(publication.revision(), "applied",
                "Published to the server.", actualProject.json()));
        plugin.getProjectTransaction().commit(transaction);
        lease.close();
      } catch (HoloUiProjectTransaction.CommittedCleanupException cleanupFailure) {
        logger.log(Level.WARNING,
            "Editor sync publication committed, but transaction backup cleanup will retry at startup.",
            cleanupFailure);
        lease.close();
      } catch (HoloUiProjectTransaction.CommitUncertainException uncertainFailure) {
        recoveryRequired.add(session.sessionId());
        logger.log(Level.SEVERE,
            "Editor sync commit durability is uncertain for session "
                + abbreviate(session.sessionId())
                + "; relay acknowledgement is paused until server restart recovery.",
            uncertainFailure);
        lease.close();
        result.completeExceptionally(uncertainFailure);
        return;
      } catch (IOException | RuntimeException commitFailure) {
        rollbackPublication(session, pendingSession, transaction, previousBoard, appliedBoard,
            lease, commitFailure, result);
        return;
      }
      sendPending(actualPendingSession).whenComplete((acknowledged, ackFailure) -> {
        if (ackFailure == null) {
          result.complete(null);
        } else {
          result.completeExceptionally(ackFailure);
        }
      });
    }, executor);
    return result;
  }

  private void rollbackPublication(EditorSyncStoredSession baseSession,
                                   EditorSyncStoredSession pendingSession,
                                   HoloUiProjectTransaction.Pending transaction,
                                   BoardDefinition previousBoard, BoardDefinition appliedBoard,
                                   HoloUiPersistenceCoordinator.ExternalTransaction lease,
                                   Throwable originalFailure, CompletableFuture<Void> result) {
    Throwable failure = rootCause(originalFailure);
    try {
      plugin.getProjectTransaction().recover();
    } catch (IOException rollbackFailure) {
      failure.addSuppressed(rollbackFailure);
      lease.close();
      result.completeExceptionally(failure);
      return;
    }
    if (!running.get()) {
      try {
        clearPending(pendingSession);
      } catch (RuntimeException stateFailure) {
        failure.addSuppressed(stateFailure);
      }
      lease.close();
      result.completeExceptionally(failure);
      return;
    }
    Map<String, String> baseMenus = menuSources(baseSession.baseProject());
    CompletableFuture<List<MenuDocument>> menuPublication =
        plugin.getConfigManager().publishEditorSyncProject(baseMenus);
    trackRollbackPublication(menuPublication);
    CompletableFuture<BoardDefinition> republished = menuPublication.thenApply(documents -> {
          if (previousBoard == null || appliedBoard == null) {
            return previousBoard;
          }
          try {
            return plugin.getBoardService().recoverExternalUpdate(appliedBoard, previousBoard);
          } catch (IOException rollbackFailure) {
            throw new CompletionException(rollbackFailure);
          }
        });
    republished.whenCompleteAsync((ignored, rollbackFailure) -> {
      try {
        clearPending(pendingSession);
      } catch (RuntimeException stateFailure) {
        failure.addSuppressed(stateFailure);
      }
      lease.close();
      if (rollbackFailure != null) {
        failure.addSuppressed(rootCause(rollbackFailure));
      }
      result.completeExceptionally(failure);
    }, executor);
  }

  private CompletableFuture<Void> queueAndSend(EditorSyncStoredSession session,
                                               EditorSyncPendingAck acknowledgement) {
    EditorSyncStoredSession pending = storePending(session, acknowledgement);
    return sendPending(pending);
  }

  private CompletableFuture<Void> sendPending(EditorSyncStoredSession session) {
    EditorSyncPendingAck acknowledgement = session.pendingAck();
    if (acknowledgement == null) {
      return CompletableFuture.completedFuture(null);
    }
    EditorSyncProject serverProject = acknowledgement.validatedServerProject(
        settings.maximumProjectBytes());
    return relay.acknowledge(session, acknowledgement.publicationRevision(),
        acknowledgement.status(), acknowledgement.message(), serverProject)
        .thenRunAsync(() -> {
          synchronized (sessionLock) {
            EditorSyncStoredSession current = sessions.get(session.sessionId());
            if (current == null || current.pendingAck() == null
                || current.pendingAck().publicationRevision()
                != acknowledgement.publicationRevision()) {
              return;
            }
            EditorSyncStoredSession acknowledged = current.acknowledgePending();
            requireStoredCapacity(current.sessionId(), acknowledged);
            sessions.put(session.sessionId(), acknowledged);
            saveSessions();
          }
        }, executor);
  }

  private EditorSyncStoredSession storePending(EditorSyncStoredSession expected,
                                               EditorSyncPendingAck acknowledgement) {
    synchronized (sessionLock) {
      EditorSyncStoredSession current = sessions.get(expected.sessionId());
      if (current != expected && (current == null
          || !current.baseRevision().equals(expected.baseRevision())
          || current.lastPublicationRevision() != expected.lastPublicationRevision())) {
        throw new CancellationException("sync session changed while processing a publication");
      }
      EditorSyncStoredSession pending = current.withPendingAck(acknowledgement);
      requireStoredCapacity(current.sessionId(), pending);
      sessions.put(pending.sessionId(), pending);
      try {
        saveSessions();
      } catch (RuntimeException failure) {
        sessions.put(current.sessionId(), current);
        throw failure;
      }
      return pending;
    }
  }

  private void clearPending(EditorSyncStoredSession expected) {
    synchronized (sessionLock) {
      EditorSyncStoredSession current = sessions.get(expected.sessionId());
      if (current == null || current.pendingAck() == null) {
        return;
      }
      EditorSyncStoredSession cleared = current.clearPendingAck();
      sessions.put(cleared.sessionId(), cleared);
      try {
        saveSessions();
      } catch (RuntimeException failure) {
        sessions.put(current.sessionId(), current);
        throw failure;
      }
    }
  }

  private Map<Path, byte[]> expectedFiles(
      EditorSyncStoredSession session,
      EditorSyncPublicationValidator.ValidatedProject publication) throws IOException {
    Path data = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    Map<String, String> baseMenus = menuSources(session.baseProject());
    Map<String, byte[]> baseImages = imageContents(session.baseProject());
    Map<Path, byte[]> expected = new LinkedHashMap<>();
    for (String id : publication.menus().keySet()) {
      String baseSource = baseMenus.get(id);
      expected.put(data.resolve("menus").resolve(id + ".json").normalize(),
          baseSource == null ? null : baseSource.getBytes(StandardCharsets.UTF_8));
    }
    for (String path : publication.images().keySet()) {
      expected.put(data.resolve("images").resolve(path).normalize(), baseImages.get(path));
    }
    if (publication.board() != null) {
      Path board = data.resolve("boards").resolve(session.subjectId() + ".json").normalize();
      expected.put(board, expectedBoardFile(board, session.baseProject()));
    }
    return Map.copyOf(expected);
  }

  static byte[] expectedBoardFile(Path boardPath, JsonObject baseProject) throws IOException {
    if (Files.isSymbolicLink(boardPath)
        || !Files.isRegularFile(boardPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("board snapshot file must be a regular non-symbolic file");
    }
    byte[] source = Files.readAllBytes(boardPath);
    BoardDefinition persisted;
    BoardDefinition captured;
    try {
      JsonElement parsed = JsonParser.parseString(new String(source, StandardCharsets.UTF_8));
      if (!parsed.isJsonObject()) {
        throw new IllegalArgumentException("board document must be an object");
      }
      persisted = GSON.fromJson(parsed, BoardDefinition.class);
      captured = GSON.fromJson(EditorSyncJson.requireObject(baseProject, "board"),
          BoardDefinition.class);
    } catch (RuntimeException failure) {
      throw new IOException("board snapshot file is invalid", failure);
    }
    if (persisted == null || captured == null || !persisted.equals(captured)) {
      throw new IOException("board snapshot file changed after the sync session was opened");
    }
    return source;
  }

  private EditorSyncProject currentProject(EditorSyncKind kind, String subjectId) {
    return kind == EditorSyncKind.MENU
        ? snapshotBuilder.menu(subjectId, settings.maximumProjectBytes())
        : snapshotBuilder.board(subjectId, settings.maximumProjectBytes());
  }

  private EditorSyncProject currentProject(EditorSyncStoredSession session, JsonObject scopeProject) {
    return scopedProjectReader.read(session, scopeProject, settings.maximumProjectBytes());
  }

  private static EditorSyncProject scopedProject(
      HoloUI plugin, EditorSyncSnapshotSource snapshotBuilder,
      EditorSyncStoredSession session, JsonObject scopeProject, int maximumBytes) {
    Map<String, String> menus = new LinkedHashMap<>();
    for (String id : menuSources(scopeProject).keySet()) {
      String source = plugin.getConfigManager().getSource(id)
          .orElseThrow(() -> new IllegalStateException("scoped sync menu is no longer loaded: " + id));
      menus.put(id, source);
    }
    Map<String, byte[]> images = new LinkedHashMap<>();
    Path imageRoot = plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("images");
    for (String path : imageContents(scopeProject).keySet()) {
      try {
        Path image = EditorSyncSnapshotBuilder.resolveConfinedFile(imageRoot, path, true);
        byte[] content = Files.readAllBytes(image);
        if (content.length > EditorSyncSnapshotBuilder.MAX_IMAGE_BYTES) {
          throw new EditorSyncProjectTooLargeException(content.length,
              EditorSyncSnapshotBuilder.MAX_IMAGE_BYTES);
        }
        EditorSyncSnapshotBuilder.detectMediaType(content);
        images.put(path, content);
      } catch (IOException failure) {
        throw new CompletionException(failure);
      }
    }
    BoardDefinition board = session.kind() == EditorSyncKind.BOARD
        ? plugin.getBoardService().get(session.subjectId())
        .orElseThrow(() -> new IllegalStateException(
            "scoped sync board is no longer loaded: " + session.subjectId()))
        : null;
    return snapshotBuilder.fromContent(session.kind(), session.subjectId(), board, menus, images,
        EditorSyncJson.requireObject(scopeProject, "constraints"),
        maximumBytes);
  }

  private void validateStoredSession(EditorSyncStoredSession session) {
    EditorSyncProject project = EditorSyncProject.validated(
        session.baseProject(), settings.maximumProjectBytes());
    if (project.kind() != session.kind() || !project.subjectId().equals(session.subjectId())) {
      throw new IllegalArgumentException("stored editor sync session identity does not match its project");
    }
    if (session.pendingAck() != null) {
      EditorSyncProject pendingProject = session.pendingAck().validatedServerProject(
          settings.maximumProjectBytes());
      if (pendingProject != null) {
        EditorSyncStoredSession pendingBase = new EditorSyncStoredSession(
            session.capability(), session.subject(), session.expiresAt(),
            session.lastPublicationRevision(), pendingProject.json(), null);
        publicationValidator.validateBase(pendingBase, settings.maximumProjectBytes());
      }
    }
    publicationValidator.validateBase(session, settings.maximumProjectBytes());
  }

  private EditorSyncStoredSession currentSession(String sessionId) {
    EditorSyncStoredSession session = sessions.get(sessionId);
    if (session == null) {
      throw new CancellationException("sync session is unavailable");
    }
    return session;
  }

  private void expire(EditorSyncStoredSession expected) {
    synchronized (sessionLock) {
      if (sessions.remove(expected.sessionId(), expected)) {
        pollBackoffs.remove(expected.sessionId());
        saveSessions();
      }
    }
  }

  private void saveSessions() {
    try {
      store.save(sessions.values());
    } catch (EditorSyncPersistenceUncertainException failure) {
      persistenceHealthy.set(false);
      logger.log(Level.SEVERE,
          "Editor sync session-store durability is uncertain; new sessions and publication pulls "
              + "are paused until restart recovery.", failure);
      throw new CompletionException(failure);
    } catch (IOException failure) {
      throw new CompletionException(failure);
    }
  }

  private void requireRunning() {
    if (!running.get()) {
      throw new IllegalStateException("editor sync service is not running");
    }
  }

  private void reserveCreate(EditorSyncProject project) {
    synchronized (sessionLock) {
      expireForCapacity(Instant.now());
      long projectBytes = project.encodedBytes();
      if (sessions.size() + reservedCreates >= EditorSyncStorageLimits.MAX_ACTIVE_SESSIONS) {
        throw new IllegalStateException("editor sync active-session limit reached");
      }
      long stored = storedSessionBytes();
      if (stored + reservedCreateBytes + projectBytes
          > EditorSyncStorageLimits.MAX_PROJECT_SNAPSHOT_BYTES) {
        throw new IllegalStateException("editor sync stored-project limit reached");
      }
      reservedCreates++;
      reservedCreateBytes += projectBytes;
    }
  }

  private void releaseCreate(EditorSyncProject project) {
    synchronized (sessionLock) {
      reservedCreates = Math.max(0, reservedCreates - 1);
      reservedCreateBytes = Math.max(0L, reservedCreateBytes - project.encodedBytes());
    }
  }

  private void requireStoredCapacity(String replacedSessionId,
                                     EditorSyncStoredSession candidate) {
    long stored = 0L;
    for (EditorSyncStoredSession session : sessions.values()) {
      if (!session.sessionId().equals(replacedSessionId)
          && !session.sessionId().equals(candidate.sessionId())) {
        stored += EditorSyncStorageLimits.bytes(session);
      }
    }
    stored += EditorSyncStorageLimits.bytes(candidate);
    if (stored > EditorSyncStorageLimits.MAX_PROJECT_SNAPSHOT_BYTES) {
      throw new IllegalStateException("editor sync stored-project limit reached");
    }
  }

  private long storedSessionBytes() {
    long stored = 0L;
    for (EditorSyncStoredSession session : sessions.values()) {
      stored += EditorSyncStorageLimits.bytes(session);
    }
    return stored;
  }

  private void expireForCapacity(Instant now) {
    boolean changed = sessions.values().removeIf(session -> session.expired(now));
    if (changed) {
      saveSessions();
    }
  }

  private EditorSyncPollBackoff backoff(String sessionId) {
    return pollBackoffs.computeIfAbsent(sessionId, ignored -> new EditorSyncPollBackoff());
  }

  void trackSchedulerPublication(CompletableFuture<?> publication) {
    synchronized (lifecycleLock) {
      if (!running.get()) {
        publication.completeExceptionally(
            new CancellationException("editor sync is shutting down"));
        return;
      }
      schedulerPublications.add(publication);
    }
    publication.whenComplete((ignored, failure) -> schedulerPublications.remove(publication));
  }

  void trackRollbackPublication(CompletableFuture<?> publication) {
    trackSchedulerPublication(publication);
  }

  static String editorUrl(String builderUrl, String endpoint, String sessionId, String editorToken) {
    if (!EditorSyncStoredSession.validCapability(sessionId)
        || !EditorSyncStoredSession.validCapability(editorToken)) {
      throw new IllegalArgumentException("invalid editor sync URL capability");
    }
    String relay = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(endpoint.getBytes(StandardCharsets.UTF_8));
    return EditorMenuHandoff.editorBase(builderUrl) + "#/sync/" + sessionId + "/"
        + editorToken + "?relay=" + relay;
  }

  public static String abbreviate(String sessionId) {
    return sessionId.length() <= 12 ? sessionId : sessionId.substring(0, 12);
  }

  private static BoardDefinition nextBoard(BoardDefinition previous, BoardDefinition changed) {
    if (previous == null) {
      return null;
    }
    if (changed == null || changed.revision() != previous.revision()
        || !changed.id().equals(previous.id()) || !changed.uuid().equals(previous.uuid())) {
      throw new IllegalArgumentException("published board identity or revision does not match the server");
    }
    if (previous.revision() == BoardDefinition.MAX_SAFE_REVISION) {
      throw new IllegalStateException("board revision overflow");
    }
    return new BoardDefinition(changed.schemaVersion(), changed.id(), changed.uuid(),
        previous.revision() + 1L, changed.rootMenuId(), changed.transform(), changed.follow(),
        changed.visibility());
  }

  static Map<String, String> normalizedMenus(Map<String, String> menus) {
    Map<String, String> normalized = new LinkedHashMap<>();
    menus.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
      String source = entry.getValue();
      normalized.put(entry.getKey(), source.endsWith("\n") || source.endsWith("\r")
          ? source
          : source + System.lineSeparator());
    });
    return Map.copyOf(normalized);
  }

  private static Map<String, String> menuSources(JsonObject project) {
    Map<String, String> menus = new LinkedHashMap<>();
    JsonArray entries = EditorSyncJson.requireArray(project, "menus");
    for (JsonElement value : entries) {
      JsonObject entry = value.getAsJsonObject();
      menus.put(EditorSyncJson.requireString(entry, "id"),
          EditorSyncJson.requireString(entry, "json"));
    }
    return Map.copyOf(menus);
  }

  private static Map<String, byte[]> imageContents(JsonObject project) {
    Map<String, byte[]> images = new LinkedHashMap<>();
    JsonArray entries = EditorSyncJson.requireArray(project, "images");
    for (JsonElement value : entries) {
      JsonObject entry = value.getAsJsonObject();
      String data = EditorSyncJson.requireString(entry, "data");
      int separator = data.indexOf(',');
      if (separator < 0) {
        throw new IllegalArgumentException("stored image data URL is malformed");
      }
      images.put(EditorSyncJson.requireString(entry, "path"),
          Base64.getDecoder().decode(data.substring(separator + 1)));
    }
    return Map.copyOf(images);
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while ((current instanceof CompletionException
        || current instanceof java.util.concurrent.ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static String safeMessage(Throwable failure) {
    String message = rootCause(failure).getMessage();
    if (message == null || message.isBlank()) {
      return rootCause(failure).getClass().getSimpleName();
    }
    return message.substring(0, Math.min(message.length(), 1000));
  }

  record Options(HoloUI plugin, EditorSyncSnapshotSource snapshotSource,
                 EditorSyncPublicationValidator publicationValidator,
                 EditorSyncRelayGateway relay, EditorSyncSessionPersistence store,
                 ScheduledExecutorService executor, java.util.logging.Logger logger,
                 EditorSyncSettingsView settings, ScopedProjectReader scopedProjectReader) {
  }

  @FunctionalInterface
  interface ScopedProjectReader {
    EditorSyncProject read(EditorSyncStoredSession session, JsonObject scopeProject,
                           int maximumBytes);
  }
}
