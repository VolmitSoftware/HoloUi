package art.arcane.holoui.board;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BoardService {
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;
  private final BoardStore store;
  private final BoardTaskRunner taskRunner;
  private final Logger logger;
  private final BoardSpatialIndex spatialIndex;
  private final CopyOnWriteArrayList<BoardServiceListener> listeners;
  private final Object lifecycleLock;
  private final ArrayDeque<PendingOperation<?>> pendingOperations;
  private final CompletableFuture<BoardLoadResult> startupFuture;
  private final Runnable beforeIndexPublication;
  private final Runnable afterPublication;
  private final HoloUiPersistenceCoordinator persistenceCoordinator;

  private Lifecycle lifecycle;
  private PendingOperation<?> activeOperation;
  private BoardTaskRunner.BoardTaskHandle activeTask;
  private volatile double maximumViewRange;

  public BoardService(JavaPlugin plugin) {
    this(dependencies(plugin));
  }

  BoardService(Dependencies dependencies) {
    this(dependencies, () -> {
    });
  }

  BoardService(Dependencies dependencies, Runnable afterPublication) {
    Dependencies requiredDependencies = Objects.requireNonNull(dependencies, "dependencies");
    this.store = requiredDependencies.store();
    this.taskRunner = requiredDependencies.taskRunner();
    this.logger = requiredDependencies.logger();
    this.beforeIndexPublication = requiredDependencies.beforeIndexPublication();
    this.afterPublication = Objects.requireNonNull(afterPublication, "afterPublication");
    this.persistenceCoordinator = requiredDependencies.persistenceCoordinator();
    this.spatialIndex = new BoardSpatialIndex();
    this.listeners = new CopyOnWriteArrayList<>();
    this.lifecycleLock = new Object();
    this.pendingOperations = new ArrayDeque<>();
    this.startupFuture = new CompletableFuture<>();
    this.lifecycle = Lifecycle.NEW;
  }

  public CompletableFuture<BoardLoadResult> start() {
    synchronized (lifecycleLock) {
      if (lifecycle == Lifecycle.RUNNING) {
        return startupFuture;
      }
      if (lifecycle == Lifecycle.STOPPED) {
        cancel(startupFuture, "board service is shut down");
        return startupFuture;
      }
      lifecycle = Lifecycle.RUNNING;
    }

    enqueue(new PendingOperation<>("load persistent boards during startup", this::loadBoards, startupFuture));
    return startupFuture;
  }

  public CompletableFuture<BoardLoadResult> reload() {
    return enqueue("reload persistent boards", this::loadBoards);
  }

  public CompletableFuture<BoardDefinition> create(BoardDefinition definition) {
    BoardDefinition requiredDefinition = Objects.requireNonNull(definition, "definition");
    return enqueue("create board '" + requiredDefinition.id() + "'", () -> {
      BoardDefinition created = store.create(requiredDefinition);
      return new WorkResult<>(created, () -> publishCreatedIndex(created),
          notificationListeners -> notifyCreated(created, notificationListeners));
    });
  }

  public CompletableFuture<BoardDefinition> update(String id, long expectedRevision,
                                                   UnaryOperator<BoardDefinition> update) {
    String canonicalId = BoardIds.canonicalize(id);
    UnaryOperator<BoardDefinition> requiredUpdate = Objects.requireNonNull(update, "update");
    return enqueue("update board '" + canonicalId + "'", () -> {
      BoardDefinition previous = store.get(canonicalId)
          .orElseThrow(() -> new NoSuchElementException("unknown board: " + canonicalId));
      BoardDefinition updated = store.update(canonicalId, expectedRevision, requiredUpdate);
      BoardUpdate publication = new BoardUpdate(previous, updated);
      return new WorkResult<>(updated, () -> publishUpdatedIndex(publication),
          notificationListeners -> notifyUpdated(publication, notificationListeners));
    });
  }

  public CompletableFuture<BoardDefinition> delete(String id, long expectedRevision) {
    String canonicalId = BoardIds.canonicalize(id);
    return enqueue("delete board '" + canonicalId + "'", () -> {
      BoardDefinition deleted = store.delete(canonicalId, expectedRevision);
      return new WorkResult<>(deleted, () -> publishDeletedIndex(deleted),
          notificationListeners -> notifyDeleted(deleted, notificationListeners));
    });
  }

  public CompletableFuture<BoardDefinition> rename(String id, String newId, long expectedRevision) {
    String canonicalId = BoardIds.canonicalize(id);
    String canonicalNewId = BoardIds.canonicalize(newId);
    return enqueue("rename board '" + canonicalId + "' to '" + canonicalNewId + "'", () -> {
      BoardDefinition previous = store.get(canonicalId)
          .orElseThrow(() -> new NoSuchElementException("unknown board: " + canonicalId));
      BoardDefinition renamed = store.rename(canonicalId, canonicalNewId, expectedRevision);
      BoardUpdate publication = new BoardUpdate(previous, renamed);
      return new WorkResult<>(renamed, () -> publishUpdatedIndex(publication),
          notificationListeners -> notifyUpdated(publication, notificationListeners));
    });
  }

  public BoardDefinition publishExternalCreate(BoardDefinition created) throws IOException {
    BoardDefinition requiredCreated = Objects.requireNonNull(created, "created");
    BoardDefinition published;
    List<BoardServiceListener> notificationListeners;
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.RUNNING) {
        throw new CancellationException("board service is not running");
      }
      published = store.publishExternalCreate(requiredCreated);
      publishCreatedIndex(published);
      notificationListeners = List.copyOf(listeners);
    }
    afterPublication.run();
    notifyCreated(published, notificationListeners);
    return published;
  }

  public BoardDefinition recoverExternalCreate(BoardDefinition created) throws IOException {
    BoardDefinition requiredCreated = Objects.requireNonNull(created, "created");
    BoardDefinition recovered;
    List<BoardServiceListener> notificationListeners;
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.RUNNING) {
        throw new CancellationException("board service is not running");
      }
      recovered = store.recoverExternalCreate(requiredCreated);
      if (spatialIndex.get(recovered.id()).isEmpty()) {
        return recovered;
      }
      publishDeletedIndex(recovered);
      notificationListeners = List.copyOf(listeners);
    }
    afterPublication.run();
    notifyDeleted(recovered, notificationListeners);
    return recovered;
  }

  public BoardDefinition publishExternalUpdate(BoardDefinition expected, BoardDefinition updated)
      throws IOException {
    BoardDefinition previous = Objects.requireNonNull(expected, "expected");
    BoardDefinition published;
    List<BoardServiceListener> notificationListeners;
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.RUNNING) {
        throw new CancellationException("board service is not running");
      }
      published = store.publishExternal(previous, Objects.requireNonNull(updated, "updated"));
      publishUpdatedIndex(new BoardUpdate(previous, published));
      notificationListeners = List.copyOf(listeners);
    }
    afterPublication.run();
    notifyUpdated(new BoardUpdate(previous, published), notificationListeners);
    return published;
  }

  public BoardDefinition recoverExternalUpdate(BoardDefinition applied, BoardDefinition restored)
      throws IOException {
    BoardDefinition requiredApplied = Objects.requireNonNull(applied, "applied");
    BoardDefinition requiredRestored = Objects.requireNonNull(restored, "restored");
    BoardDefinition published;
    List<BoardServiceListener> notificationListeners;
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.RUNNING) {
        throw new CancellationException("board service is not running");
      }
      published = store.recoverExternal(requiredApplied, requiredRestored);
      if (published.equals(spatialIndex.get(published.id()).orElse(null))) {
        return published;
      }
      publishUpdatedIndex(new BoardUpdate(requiredApplied, published));
      notificationListeners = List.copyOf(listeners);
    }
    afterPublication.run();
    notifyUpdated(new BoardUpdate(requiredApplied, published), notificationListeners);
    return published;
  }

  public Optional<BoardDefinition> get(String id) {
    return spatialIndex.get(id);
  }

  public Optional<BoardDefinition> get(UUID boardUuid) {
    return spatialIndex.get(boardUuid);
  }

  public List<BoardDefinition> list() {
    return spatialIndex.list();
  }

  public List<BoardDefinition> query(UUID worldUuid, double x, double z, double radius) {
    return spatialIndex.query(worldUuid, x, z, radius);
  }

  public int size() {
    return spatialIndex.size();
  }

  public double maximumViewRange() {
    return maximumViewRange;
  }

  public boolean isRunning() {
    synchronized (lifecycleLock) {
      return lifecycle == Lifecycle.RUNNING;
    }
  }

  public void addListener(BoardServiceListener listener) {
    listeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
  }

  public List<BoardDefinition> subscribeAndSnapshot(BoardServiceListener listener) {
    BoardServiceListener requiredListener = Objects.requireNonNull(listener, "listener");
    synchronized (lifecycleLock) {
      listeners.addIfAbsent(requiredListener);
      return spatialIndex.list();
    }
  }

  public void removeListener(BoardServiceListener listener) {
    listeners.remove(Objects.requireNonNull(listener, "listener"));
  }

  public void shutdown() {
    List<PendingOperation<?>> cancelledOperations;
    PendingOperation<?> cancelledActive;
    synchronized (lifecycleLock) {
      if (lifecycle == Lifecycle.STOPPED) {
        return;
      }
      lifecycle = Lifecycle.STOPPED;
      cancelledOperations = List.copyOf(pendingOperations);
      pendingOperations.clear();
      cancelledActive = activeOperation;
      activeOperation = null;
      activeTask = null;
    }

    listeners.clear();
    cancel(startupFuture, "board service shut down before startup completed");
    if (cancelledActive != null) {
      cancel(cancelledActive.future(), "board service shut down while the operation was active");
    }
    for (PendingOperation<?> operation : cancelledOperations) {
      cancel(operation.future(), "board service shut down before the operation started");
    }
    taskRunner.shutdown();
    boolean interrupted = false;
    while (true) {
      try {
        if (taskRunner.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          break;
        }
        logger.warning("Still waiting for the active board storage operation to finish before shutdown.");
      } catch (InterruptedException interruption) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private WorkResult<BoardLoadResult> loadBoards() throws IOException {
    BoardLoadResult result = store.load();
    List<BoardDefinition> loadedBoards = store.list();
    BoardReload publication = new BoardReload(result, loadedBoards);
    return new WorkResult<>(result, () -> publishReloadedIndex(publication),
        notificationListeners -> notifyReloaded(publication, notificationListeners));
  }

  private void publishCreatedIndex(BoardDefinition board) {
    spatialIndex.upsert(board);
    refreshMaximumViewRange();
  }

  private void notifyCreated(BoardDefinition board, List<BoardServiceListener> notificationListeners) {
    for (BoardServiceListener listener : notificationListeners) {
      notifyListener("created board '" + board.id() + "'", () -> listener.boardCreated(board));
    }
  }

  private void publishUpdatedIndex(BoardUpdate publication) {
    spatialIndex.upsert(publication.updated());
    refreshMaximumViewRange();
  }

  private void notifyUpdated(BoardUpdate publication, List<BoardServiceListener> notificationListeners) {
    for (BoardServiceListener listener : notificationListeners) {
      notifyListener("updated board '" + publication.updated().id() + "'",
          () -> listener.boardUpdated(publication.previous(), publication.updated()));
    }
  }

  private void publishDeletedIndex(BoardDefinition board) {
    spatialIndex.remove(board.uuid());
    refreshMaximumViewRange();
  }

  private void notifyDeleted(BoardDefinition board, List<BoardServiceListener> notificationListeners) {
    for (BoardServiceListener listener : notificationListeners) {
      notifyListener("deleted board '" + board.id() + "'", () -> listener.boardDeleted(board));
    }
  }

  private void publishReloadedIndex(BoardReload publication) {
    spatialIndex.replaceAll(publication.boards());
    refreshMaximumViewRange();
  }

  private void notifyReloaded(BoardReload publication, List<BoardServiceListener> notificationListeners) {
    if (!isRunning()) {
      return;
    }
    logReload(publication.result(), publication.boards().size());
    for (BoardServiceListener listener : notificationListeners) {
      notifyListener("reloaded persistent boards",
          () -> listener.boardsReloaded(publication.result(), publication.boards()));
    }
  }

  private void refreshMaximumViewRange() {
    double highestRange = 0.0D;
    for (BoardDefinition board : spatialIndex.list()) {
      highestRange = Math.max(highestRange, board.visibility().viewRange());
    }
    maximumViewRange = highestRange;
  }

  private void logReload(BoardLoadResult result, int publishedCount) {
    if (result.failures().isEmpty()) {
      logger.info("Published " + publishedCount + " persistent board(s) from " + store.directory() + ".");
      return;
    }

    logger.warning("Published " + publishedCount + " persistent board(s) from " + store.directory()
        + "; " + result.failures().size() + " board file(s) failed validation. "
        + result.retained() + " last-good definition(s) were retained.");
    for (Map.Entry<String, String> failure : result.failures().entrySet()) {
      logger.warning("Board file '" + failure.getKey() + "' was not loaded: " + failure.getValue());
    }
  }

  private void notifyListener(String operation, Runnable notification) {
    if (!isRunning()) {
      return;
    }
    try {
      notification.run();
    } catch (RuntimeException failure) {
      logger.log(Level.SEVERE, "A board listener failed after HoloUI " + operation + ".", failure);
    }
  }

  private <T> CompletableFuture<T> enqueue(String description, BoardWork<T> work) {
    CompletableFuture<T> future = new CompletableFuture<>();
    enqueue(new PendingOperation<>(description, work, future));
    return future;
  }

  private void enqueue(PendingOperation<?> operation) {
    PendingOperation<?> nextOperation;
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.RUNNING) {
        cancel(operation.future(), "board service is not running");
        return;
      }
      pendingOperations.addLast(operation);
      nextOperation = reserveNextOperation();
    }
    if (nextOperation != null) {
      dispatch(nextOperation);
    }
  }

  private PendingOperation<?> reserveNextOperation() {
    if (activeOperation != null || pendingOperations.isEmpty() || lifecycle != Lifecycle.RUNNING) {
      return null;
    }
    activeOperation = pendingOperations.removeFirst();
    return activeOperation;
  }

  private void dispatch(PendingOperation<?> operation) {
    BoardTaskRunner.BoardTaskHandle task;
    try {
      task = taskRunner.submit(() -> execute(operation));
    } catch (RuntimeException failure) {
      rejectDispatch(operation, failure);
      return;
    }
    if (task == null) {
      rejectDispatch(operation, new IllegalStateException("the asynchronous scheduler rejected the operation"));
      return;
    }

    boolean cancelTask;
    synchronized (lifecycleLock) {
      cancelTask = lifecycle != Lifecycle.RUNNING || activeOperation != operation;
      if (!cancelTask) {
        activeTask = task;
      }
    }
    if (cancelTask) {
      task.cancel();
    }
  }

  private void execute(PendingOperation<?> operation) {
    if (!isActive(operation)) {
      finish(operation);
      return;
    }

    try {
      WorkResult<?> result = persistenceCoordinator.write(() -> persistAndPublish(operation));
      complete(operation, result.value());
    } catch (Exception failure) {
      fail(operation, failure);
    } finally {
      finish(operation);
    }
  }

  private WorkResult<?> persistAndPublish(PendingOperation<?> operation) throws Exception {
    WorkResult<?> result = operation.work().execute();
    beforeIndexPublication.run();
    List<BoardServiceListener> notificationListeners =
        publish(operation, result.indexPublication());
    if (notificationListeners == null) {
      throw new CancellationException(
          "board service shut down before the operation could be published");
    }
    afterPublication.run();
    result.notification().accept(notificationListeners);
    return result;
  }

  private List<BoardServiceListener> publish(PendingOperation<?> operation, Runnable publication) {
    synchronized (lifecycleLock) {
      if (lifecycle != Lifecycle.RUNNING || activeOperation != operation) {
        return null;
      }
      publication.run();
      return List.copyOf(listeners);
    }
  }

  private boolean isActive(PendingOperation<?> operation) {
    synchronized (lifecycleLock) {
      return lifecycle == Lifecycle.RUNNING && activeOperation == operation;
    }
  }

  private void rejectDispatch(PendingOperation<?> operation, RuntimeException failure) {
    if (!operation.future().isDone()) {
      logFailure(operation.description(), failure);
      operation.future().completeExceptionally(failure);
    }
    finish(operation);
  }

  private void fail(PendingOperation<?> operation, Exception failure) {
    if (operation.future().isDone()) {
      return;
    }
    logFailure(operation.description(), failure);
    operation.future().completeExceptionally(failure);
  }

  private void logFailure(String operation, Exception failure) {
    if (failure instanceof BoardRevisionConflictException
        || failure instanceof NoSuchElementException
        || failure instanceof FileAlreadyExistsException
        || failure instanceof IllegalArgumentException) {
      return;
    }
    logger.log(Level.SEVERE, "Unable to " + operation + " in " + store.directory() + ".", failure);
  }

  private void finish(PendingOperation<?> operation) {
    PendingOperation<?> nextOperation;
    synchronized (lifecycleLock) {
      if (activeOperation != operation) {
        return;
      }
      activeOperation = null;
      activeTask = null;
      nextOperation = reserveNextOperation();
    }
    if (nextOperation != null) {
      dispatch(nextOperation);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void complete(PendingOperation<?> operation, Object result) {
    PendingOperation<T> typedOperation = (PendingOperation<T>) operation;
    typedOperation.future().complete((T) result);
  }

  private static void cancel(CompletableFuture<?> future, String reason) {
    future.completeExceptionally(new CancellationException(reason));
  }

  private static Dependencies dependencies(JavaPlugin plugin) {
    JavaPlugin requiredPlugin = Objects.requireNonNull(plugin, "plugin");
    BoardTaskRunner taskRunner = new BoardExecutorTaskRunner(requiredPlugin.getClass().getClassLoader());
    HoloUiPersistenceCoordinator coordinator = requiredPlugin instanceof HoloUI holoUi
        ? holoUi.getPersistenceCoordinator()
        : new HoloUiPersistenceCoordinator();
    return new Dependencies(new BoardRepository(requiredPlugin.getDataFolder()), taskRunner,
        requiredPlugin.getLogger(), coordinator, () -> {
        });
  }

  record Dependencies(BoardStore store, BoardTaskRunner taskRunner, Logger logger,
                      HoloUiPersistenceCoordinator persistenceCoordinator,
                      Runnable beforeIndexPublication) {
    Dependencies {
      store = Objects.requireNonNull(store, "store");
      taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
      logger = Objects.requireNonNull(logger, "logger");
      persistenceCoordinator = Objects.requireNonNull(persistenceCoordinator, "persistenceCoordinator");
      beforeIndexPublication = Objects.requireNonNull(
          beforeIndexPublication, "beforeIndexPublication");
    }
  }

  @FunctionalInterface
  private interface BoardWork<T> {
    WorkResult<T> execute() throws Exception;
  }

  private record WorkResult<T>(T value, Runnable indexPublication,
                               Consumer<List<BoardServiceListener>> notification) {
    private WorkResult {
      value = Objects.requireNonNull(value, "value");
      indexPublication = Objects.requireNonNull(indexPublication, "indexPublication");
      notification = Objects.requireNonNull(notification, "notification");
    }
  }

  private record PendingOperation<T>(String description, BoardWork<T> work, CompletableFuture<T> future) {
    private PendingOperation {
      description = Objects.requireNonNull(description, "description");
      work = Objects.requireNonNull(work, "work");
      future = Objects.requireNonNull(future, "future");
    }
  }

  private record BoardUpdate(BoardDefinition previous, BoardDefinition updated) {
  }

  private record BoardReload(BoardLoadResult result, List<BoardDefinition> boards) {
    private BoardReload {
      boards = List.copyOf(boards);
    }
  }

  private enum Lifecycle {
    NEW,
    RUNNING,
    STOPPED
  }
}
