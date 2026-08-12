package art.arcane.holoui.board;

import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BoardServiceTest {
  private static final UUID WORLD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000201");

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void mutationsAreSerializedAndPublishedBeforeListenersRun() throws IOException {
    File pluginData = temp.newFolder("publication");
    ManualTaskRunner runner = new ManualTaskRunner();
    BoardService service = service(new BoardRepository(pluginData), runner);
    RecordingListener listener = new RecordingListener(service);
    service.addListener(listener);

    CompletableFuture<BoardLoadResult> startup = service.start();
    assertTrue(service.list().isEmpty());
    assertEquals(1, runner.size());
    runner.runNext();
    assertTrue(startup.join().successful());
    assertEquals(1, listener.reloads.size());

    BoardDefinition createdInput = board("spawn/main", 0.0D, 0.0D)
        .withVisibility(BoardVisibility.publicAccess().withRanges(96.0D, 8.0D));
    CompletableFuture<BoardDefinition> createdFuture = service.create(createdInput);
    CompletableFuture<BoardDefinition> updatedFuture = service.update(createdInput.id(),
        BoardDefinition.INITIAL_REVISION, board -> board.withTransform(
            BoardTransform.at("example:world", WORLD_UUID, 32.0D, 70.0D, -4.0D, 90.0D)));

    assertEquals(1, runner.size());
    assertTrue(service.list().isEmpty());
    runner.runNext();
    BoardDefinition created = createdFuture.join();
    assertEquals(1, runner.size());
    assertEquals(created, service.get(created.id()).orElseThrow());
    assertEquals(96.0D, service.maximumViewRange(), 0.0D);
    assertEquals(List.of(created), listener.created);
    assertTrue(listener.allCallbacksObservedPublishedState);

    runner.runNext();
    BoardDefinition updated = updatedFuture.join();
    assertEquals(created.revision() + 1L, updated.revision());
    assertEquals(List.of(updated), service.query(WORLD_UUID, 32.0D, -4.0D, 0.0D));
    assertEquals(List.of(updated), listener.updated);
    assertTrue(listener.allCallbacksObservedPublishedState);

    CompletableFuture<BoardDefinition> renamedFuture = service.rename(updated.id(), "lobbies/info", updated.revision());
    runner.runNext();
    BoardDefinition renamed = renamedFuture.join();
    assertFalse(service.get(updated.id()).isPresent());
    assertEquals(renamed, service.get("LOBBIES/INFO").orElseThrow());
    assertEquals(created.uuid(), renamed.uuid());
    assertEquals(updated.revision() + 1L, renamed.revision());

    CompletableFuture<BoardDefinition> deletedFuture = service.delete(renamed.id(), renamed.revision());
    runner.runNext();
    assertEquals(renamed, deletedFuture.join());
    assertTrue(service.list().isEmpty());
    assertEquals(0.0D, service.maximumViewRange(), 0.0D);
    assertEquals(List.of(renamed), listener.deleted);
    assertTrue(listener.allCallbacksObservedPublishedState);
  }

  @Test
  public void repositoryFailureAndStaleRevisionNeverPublish() throws IOException {
    ManualTaskRunner failingRunner = new ManualTaskRunner();
    FailingCreateStore failingStore = new FailingCreateStore(temp.newFolder("failing").toPath());
    BoardService failingService = service(failingStore, failingRunner);
    RecordingListener failingListener = new RecordingListener(failingService);
    failingService.addListener(failingListener);
    failingService.start();
    failingRunner.runNext();

    CompletableFuture<BoardDefinition> failedCreate = failingService.create(board("failed", 0.0D, 0.0D));
    failingRunner.runNext();
    CompletionException diskFailure = assertThrows(CompletionException.class, failedCreate::join);
    assertTrue(diskFailure.getCause() instanceof IOException);
    assertTrue(failingService.list().isEmpty());
    assertTrue(failingListener.created.isEmpty());

    ManualTaskRunner revisionRunner = new ManualTaskRunner();
    BoardService revisionService = service(new BoardRepository(temp.newFolder("revision")), revisionRunner);
    RecordingListener revisionListener = new RecordingListener(revisionService);
    revisionService.addListener(revisionListener);
    revisionService.start();
    revisionRunner.runNext();
    CompletableFuture<BoardDefinition> createdFuture = revisionService.create(board("revision", 0.0D, 0.0D));
    revisionRunner.runNext();
    BoardDefinition created = createdFuture.join();

    CompletableFuture<BoardDefinition> staleUpdate = revisionService.update(created.id(),
        created.revision() + 1L, board -> board.withRootMenu("Other"));
    revisionRunner.runNext();
    CompletionException revisionFailure = assertThrows(CompletionException.class, staleUpdate::join);
    assertTrue(revisionFailure.getCause() instanceof BoardRevisionConflictException);
    assertEquals(created, revisionService.get(created.id()).orElseThrow());
    assertTrue(revisionListener.updated.isEmpty());
  }

  @Test
  public void subscriptionReturnsThePublishedSnapshotBeforeLaterEvents() throws IOException {
    File pluginData = temp.newFolder("subscribe");
    BoardRepository seedRepository = new BoardRepository(pluginData);
    seedRepository.load();
    BoardDefinition seeded = seedRepository.create(board("existing", 0.0D, 0.0D));
    ManualTaskRunner runner = new ManualTaskRunner();
    BoardService service = service(new BoardRepository(pluginData), runner);
    service.start();
    runner.runNext();
    RecordingListener listener = new RecordingListener(service);

    List<BoardDefinition> snapshot = service.subscribeAndSnapshot(listener);
    CompletableFuture<BoardDefinition> createdFuture = service.create(board("later", 8.0D, 8.0D));
    runner.runNext();
    BoardDefinition created = createdFuture.join();

    assertEquals(List.of(seeded), snapshot);
    assertEquals(List.of(created), listener.created);
    assertTrue(listener.allCallbacksObservedPublishedState);
  }

  @Test
  public void subscriberAfterPublicationReceivesTheSnapshotWithoutADuplicateEvent() throws IOException {
    ManualTaskRunner runner = new ManualTaskRunner();
    AtomicBoolean subscribeAfterPublication = new AtomicBoolean();
    AtomicReference<BoardService> serviceReference = new AtomicReference<>();
    AtomicReference<List<BoardDefinition>> lateSnapshot = new AtomicReference<>();
    AtomicReference<RecordingListener> lateListener = new AtomicReference<>();
    Logger logger = Logger.getLogger(BoardServiceTest.class.getName() + ".linearized-subscription");
    logger.setLevel(Level.OFF);
    BoardService service = new BoardService(new BoardService.Dependencies(
        new BoardRepository(temp.newFolder("linearized-subscription")), runner, logger,
        new HoloUiPersistenceCoordinator()), () -> {
      if (subscribeAfterPublication.compareAndSet(true, false)) {
        lateSnapshot.set(serviceReference.get().subscribeAndSnapshot(lateListener.get()));
      }
    });
    serviceReference.set(service);
    RecordingListener early = new RecordingListener(service);
    RecordingListener late = new RecordingListener(service);
    lateListener.set(late);
    service.addListener(early);
    service.start();
    runner.runNext();

    subscribeAfterPublication.set(true);
    CompletableFuture<BoardDefinition> firstFuture = service.create(board("first", 0.0D, 0.0D));
    runner.runNext();
    BoardDefinition first = firstFuture.join();

    assertEquals(List.of(first), lateSnapshot.get());
    assertEquals(List.of(first), early.created);
    assertTrue(late.created.isEmpty());

    CompletableFuture<BoardDefinition> secondFuture = service.create(board("second", 8.0D, 8.0D));
    runner.runNext();
    BoardDefinition second = secondFuture.join();

    assertEquals(List.of(first, second), early.created);
    assertEquals(List.of(second), late.created);
  }

  @Test
  public void shutdownCancelsActiveAndQueuedOperationsAndRejectsNewWork() throws IOException {
    ManualTaskRunner runner = new ManualTaskRunner();
    BoardService service = service(new BoardRepository(temp.newFolder("shutdown")), runner);

    CompletableFuture<BoardLoadResult> startup = service.start();
    CompletableFuture<BoardDefinition> queuedCreate = service.create(board("queued", 0.0D, 0.0D));
    assertEquals(1, runner.size());

    service.shutdown();

    assertFalse(service.isRunning());
    assertThrows(CancellationException.class, startup::join);
    assertThrows(CancellationException.class, queuedCreate::join);
    runner.runAll();
    assertTrue(service.list().isEmpty());

    CompletableFuture<BoardLoadResult> rejectedReload = service.reload();
    CompletableFuture<BoardDefinition> rejectedCreate = service.create(board("rejected", 0.0D, 0.0D));
    assertThrows(CancellationException.class, rejectedReload::join);
    assertThrows(CancellationException.class, rejectedCreate::join);
    assertEquals(0, runner.size());
  }

  @Test
  public void shutdownDuringDispatchWaitsForTheActiveWriteWithoutInterruptingIt() throws Exception {
    BlockingCreateStore store = new BlockingCreateStore(temp.newFolder("active-shutdown"));
    DispatchRaceTaskRunner runner = new DispatchRaceTaskRunner();
    BoardService service = service(store, runner);
    assertTrue(service.start().get(5L, TimeUnit.SECONDS).successful());
    BoardDefinition input = board("active", 0.0D, 0.0D);
    AtomicReference<CompletableFuture<BoardDefinition>> writeReference = new AtomicReference<>();
    AtomicReference<Throwable> createFailure = new AtomicReference<>();
    Thread create = new Thread(() -> {
      try {
        writeReference.set(service.create(input));
      } catch (Throwable failure) {
        createFailure.set(failure);
      }
    }, "board-service-create-test");
    Thread shutdown = new Thread(service::shutdown, "board-service-shutdown-test");

    try {
      create.start();
      assertTrue(store.createStarted.await(5L, TimeUnit.SECONDS));
      shutdown.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
      while (service.isRunning() && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertFalse(service.isRunning());
      runner.allowSubmissionReturn.countDown();
      create.join(TimeUnit.SECONDS.toMillis(5L));

      assertFalse(create.isAlive());
      assertNull(createFailure.get());
      assertNotNull(writeReference.get());
      assertTrue(shutdown.isAlive());
      assertFalse(store.interrupted.get());
      assertFalse(runner.submitInterrupted.get());

      store.allowCreate.countDown();
      shutdown.join(TimeUnit.SECONDS.toMillis(5L));

      assertFalse(shutdown.isAlive());
      assertFalse(store.interrupted.get());
      assertThrows(CancellationException.class, writeReference.get()::join);
      assertEquals(input, store.get(input.id()).orElseThrow());
      assertTrue(service.list().isEmpty());
    } finally {
      runner.allowSubmissionReturn.countDown();
      store.allowCreate.countDown();
      create.join(TimeUnit.SECONDS.toMillis(5L));
      if (shutdown.isAlive()) {
        shutdown.join(TimeUnit.SECONDS.toMillis(5L));
      }
    }
  }

  private static BoardService service(BoardStore store, BoardTaskRunner taskRunner) {
    Logger logger = Logger.getLogger(BoardServiceTest.class.getName() + "." + UUID.randomUUID());
    logger.setLevel(Level.OFF);
    return new BoardService(new BoardService.Dependencies(
        store, taskRunner, logger, new HoloUiPersistenceCoordinator()));
  }

  private static BoardDefinition board(String id, double x, double z) {
    return BoardDefinition.create(id, "menu",
        BoardTransform.at("example:world", WORLD_UUID, x, 64.0D, z, 0.0D));
  }

  private static final class ManualTaskRunner implements BoardTaskRunner {
    private final ArrayDeque<ManualTask> tasks = new ArrayDeque<>();

    @Override
    public BoardTaskHandle submit(Runnable task) {
      ManualTask scheduled = new ManualTask(task);
      tasks.addLast(scheduled);
      return scheduled::cancel;
    }

    private int size() {
      return tasks.size();
    }

    private void runNext() {
      ManualTask task = tasks.removeFirst();
      task.run();
    }

    private void runAll() {
      while (!tasks.isEmpty()) {
        runNext();
      }
    }
  }

  private static final class DispatchRaceTaskRunner implements BoardTaskRunner {
    private final BoardExecutorTaskRunner delegate = new BoardExecutorTaskRunner(
        BoardServiceTest.class.getClassLoader());
    private final AtomicInteger submissions = new AtomicInteger();
    private final CountDownLatch allowSubmissionReturn = new CountDownLatch(1);
    private final AtomicBoolean submitInterrupted = new AtomicBoolean();

    @Override
    public BoardTaskHandle submit(Runnable task) {
      BoardTaskHandle handle = delegate.submit(task);
      if (submissions.incrementAndGet() != 2) {
        return handle;
      }
      try {
        allowSubmissionReturn.await();
      } catch (InterruptedException interruption) {
        submitInterrupted.set(true);
        Thread.currentThread().interrupt();
      }
      return handle;
    }

    @Override
    public void shutdown() {
      delegate.shutdown();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return delegate.awaitTermination(timeout, unit);
    }
  }

  private static final class ManualTask {
    private final Runnable action;
    private boolean cancelled;

    private ManualTask(Runnable action) {
      this.action = action;
    }

    private void cancel() {
      cancelled = true;
    }

    private void run() {
      if (!cancelled) {
        action.run();
      }
    }
  }

  private static final class RecordingListener implements BoardServiceListener {
    private final BoardService service;
    private final List<BoardDefinition> created = new ArrayList<>();
    private final List<BoardDefinition> updated = new ArrayList<>();
    private final List<BoardDefinition> deleted = new ArrayList<>();
    private final List<BoardLoadResult> reloads = new ArrayList<>();
    private boolean allCallbacksObservedPublishedState = true;

    private RecordingListener(BoardService service) {
      this.service = service;
    }

    @Override
    public void boardCreated(BoardDefinition board) {
      created.add(board);
      allCallbacksObservedPublishedState &= service.get(board.id()).orElse(null) == board;
    }

    @Override
    public void boardUpdated(BoardDefinition previous, BoardDefinition updatedBoard) {
      updated.add(updatedBoard);
      allCallbacksObservedPublishedState &= service.get(updatedBoard.id()).orElse(null) == updatedBoard;
      allCallbacksObservedPublishedState &= previous.id().equals(updatedBoard.id())
          || service.get(previous.id()).isEmpty();
    }

    @Override
    public void boardDeleted(BoardDefinition board) {
      deleted.add(board);
      allCallbacksObservedPublishedState &= service.get(board.id()).isEmpty();
    }

    @Override
    public void boardsReloaded(BoardLoadResult result, List<BoardDefinition> boards) {
      reloads.add(result);
      allCallbacksObservedPublishedState &= service.list().equals(boards);
    }
  }

  private static final class FailingCreateStore implements BoardStore {
    private final Path directory;

    private FailingCreateStore(Path directory) {
      this.directory = directory;
    }

    @Override
    public Path directory() {
      return directory;
    }

    @Override
    public BoardLoadResult load() {
      return new BoardLoadResult(0, 0, 0, java.util.Map.of());
    }

    @Override
    public Optional<BoardDefinition> get(String id) {
      return Optional.empty();
    }

    @Override
    public List<BoardDefinition> list() {
      return List.of();
    }

    @Override
    public BoardDefinition create(BoardDefinition definition) throws IOException {
      throw new IOException("simulated storage failure");
    }

    @Override
    public BoardDefinition update(String id, long expectedRevision,
                                  UnaryOperator<BoardDefinition> update) {
      throw new NoSuchElementException(id);
    }

    @Override
    public BoardDefinition rename(String id, String newId, long expectedRevision) {
      throw new NoSuchElementException(id);
    }

    @Override
    public BoardDefinition delete(String id, long expectedRevision) {
      throw new NoSuchElementException(id);
    }

    @Override
    public BoardDefinition publishExternal(BoardDefinition expected, BoardDefinition updated) {
      throw new NoSuchElementException(expected.id());
    }

    @Override
    public BoardDefinition recoverExternal(BoardDefinition applied, BoardDefinition restored) {
      throw new NoSuchElementException(applied.id());
    }
  }

  private static final class BlockingCreateStore implements BoardStore {
    private final BoardRepository delegate;
    private final CountDownLatch createStarted = new CountDownLatch(1);
    private final CountDownLatch allowCreate = new CountDownLatch(1);
    private final AtomicBoolean interrupted = new AtomicBoolean();

    private BlockingCreateStore(File pluginData) {
      this.delegate = new BoardRepository(pluginData);
    }

    @Override
    public Path directory() {
      return delegate.directory();
    }

    @Override
    public BoardLoadResult load() throws IOException {
      return delegate.load();
    }

    @Override
    public Optional<BoardDefinition> get(String id) {
      return delegate.get(id);
    }

    @Override
    public List<BoardDefinition> list() {
      return delegate.list();
    }

    @Override
    public BoardDefinition create(BoardDefinition definition) throws IOException {
      createStarted.countDown();
      try {
        allowCreate.await();
      } catch (InterruptedException interruption) {
        interrupted.set(true);
      }
      return delegate.create(definition);
    }

    @Override
    public BoardDefinition update(String id, long expectedRevision,
                                  UnaryOperator<BoardDefinition> update) throws IOException {
      return delegate.update(id, expectedRevision, update);
    }

    @Override
    public BoardDefinition rename(String id, String newId, long expectedRevision) throws IOException {
      return delegate.rename(id, newId, expectedRevision);
    }

    @Override
    public BoardDefinition delete(String id, long expectedRevision) throws IOException {
      return delegate.delete(id, expectedRevision);
    }

    @Override
    public BoardDefinition publishExternal(BoardDefinition expected, BoardDefinition updated)
        throws IOException {
      return delegate.publishExternal(expected, updated);
    }

    @Override
    public BoardDefinition recoverExternal(BoardDefinition applied, BoardDefinition restored)
        throws IOException {
      return delegate.recoverExternal(applied, restored);
    }
  }
}
