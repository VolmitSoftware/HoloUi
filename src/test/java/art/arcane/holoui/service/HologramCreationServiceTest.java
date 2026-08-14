package art.arcane.holoui.service;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardTransform;
import art.arcane.holoui.config.menu.MenuDocument;
import art.arcane.holoui.config.menu.MenuDocumentParser;
import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import art.arcane.holoui.persistence.HoloUiProjectTransaction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HologramCreationServiceTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void boardPublicationFailureRollsBackTheMenuAndBothFiles() throws Exception {
    Path data = temp.newFolder("rollback").toPath();
    MemoryMenus menus = new MemoryMenus(data, false);
    MemoryBoards boards = new MemoryBoards(data, true, false, false);
    HologramCreationService service = service(
        data, menus, boards, new HoloUiPersistenceCoordinator());
    BoardTransform transform = transform();

    try {
      CompletionException failure = assertThrows(CompletionException.class,
          () -> service.create("spawn/welcome", "<gold>Welcome", transform).join());

      assertTrue(failure.getCause() instanceof IOException);
      assertTrue(menus.current().isEmpty());
      assertTrue(boards.current("spawn/welcome").isEmpty());
      assertFalse(Files.exists(data.resolve("menus/spawn/welcome.json")));
      assertFalse(Files.exists(data.resolve("boards/spawn/welcome.json")));
    } finally {
      service.shutdown();
    }
  }

  @Test
  public void successfulCreationPublishesOneMatchingMenuAndBoard() throws Exception {
    Path data = temp.newFolder("success").toPath();
    MemoryMenus menus = new MemoryMenus(data, false);
    MemoryBoards boards = new MemoryBoards(data, false, false, false);
    HologramCreationService service = service(
        data, menus, boards, new HoloUiPersistenceCoordinator());

    try {
      HologramCreationService.Creation created = service.create(
          "Spawn/Welcome", "<gold>Welcome home", transform()).join();

      assertEquals("spawn/welcome", created.menu().id());
      assertEquals("spawn/welcome", created.board().id());
      assertEquals(created.menu(), menus.current().orElseThrow());
      assertEquals(created.board(), boards.current(created.board().id()).orElseThrow());
      assertTrue(Files.isRegularFile(data.resolve("menus/spawn/welcome.json")));
      assertTrue(Files.isRegularFile(data.resolve("boards/spawn/welcome.json")));
    } finally {
      service.shutdown();
    }
  }

  @Test
  public void preCommitFailureRollsBackBothPublishedRuntimeEntries() throws Exception {
    Path data = temp.newFolder("commit-failure").toPath();
    MemoryMenus menus = new MemoryMenus(data, false);
    MemoryBoards boards = new MemoryBoards(data, false, true, false);
    HologramCreationService service = service(
        data, menus, boards, new HoloUiPersistenceCoordinator());

    try {
      assertThrows(CompletionException.class,
          () -> service.create("spawn/welcome", "Welcome", transform()).join());

      assertTrue(menus.current().isEmpty());
      assertTrue(boards.current("spawn/welcome").isEmpty());
      assertFalse(Files.exists(data.resolve("menus/spawn/welcome.json")));
      assertFalse(Files.exists(data.resolve("boards/spawn/welcome.json")));
    } finally {
      service.shutdown();
    }
  }

  @Test
  public void incompleteRuntimeRecoveryRequiresRestartBeforeAnotherCreate() throws Exception {
    Path data = temp.newFolder("recovery-failure").toPath();
    MemoryMenus menus = new MemoryMenus(data, true);
    MemoryBoards boards = new MemoryBoards(data, true, false, false);
    HoloUiPersistenceCoordinator coordinator = new HoloUiPersistenceCoordinator();
    HologramCreationService service = service(data, menus, boards, coordinator);

    try {
      CompletionException failure = assertThrows(CompletionException.class,
          () -> service.create("spawn/welcome", "Welcome", transform()).join());

      assertTrue(failure.getCause()
          instanceof HologramCreationService.DurabilityUncertainException);
      assertFalse(Files.exists(data.resolve("menus/spawn/welcome.json")));
      assertFalse(Files.exists(data.resolve("boards/spawn/welcome.json")));
      assertTrue(coordinator.recoveryRequired());
      assertThrows(CancellationException.class,
          () -> service.create("spawn/other", "Other", transform()).join());
    } finally {
      service.shutdown();
    }
  }

  @Test
  public void incompletePersistentRecoveryQuarantinesEveryFurtherWrite() throws Exception {
    Path data = temp.newFolder("persistent-recovery-failure").toPath();
    MemoryMenus menus = new MemoryMenus(data, false);
    MemoryBoards boards = new MemoryBoards(data, true, false, true);
    HoloUiPersistenceCoordinator coordinator = new HoloUiPersistenceCoordinator();
    HologramCreationService service = service(data, menus, boards, coordinator);

    try {
      CompletionException failure = assertThrows(CompletionException.class,
          () -> service.create("spawn/welcome", "Welcome", transform()).join());

      assertTrue(failure.getCause()
          instanceof HologramCreationService.DurabilityUncertainException);
      assertTrue(coordinator.recoveryRequired());
      assertThrows(IllegalStateException.class,
          () -> coordinator.write(() -> null));
      assertThrows(CancellationException.class,
          () -> service.create("spawn/other", "Other", transform()).join());
    } finally {
      service.shutdown();
    }
  }

  @Test
  public void stopAcceptingThenEditorLeaseReleaseLetsShutdownDrainBlockedCreate() throws Exception {
    Path data = temp.newFolder("shutdown-order").toPath();
    MemoryMenus menus = new MemoryMenus(data, false);
    MemoryBoards boards = new MemoryBoards(data, false, false, false);
    HoloUiPersistenceCoordinator coordinator = new HoloUiPersistenceCoordinator();
    Logger logger = Logger.getAnonymousLogger();
    logger.setLevel(Level.OFF);
    logger.setUseParentHandlers(false);
    AtomicReference<Thread> worker = new AtomicReference<>();
    ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "hologram-shutdown-test");
      worker.set(thread);
      return thread;
    });
    HologramCreationService service = new HologramCreationService(
        new HologramCreationService.Dependencies(
            data, logger, coordinator, new HoloUiProjectTransaction(data), menus, boards),
        executor);
    HoloUiPersistenceCoordinator.ExternalTransaction editorLease =
        coordinator.beginExternalTransaction();
    try {
      CompletableFuture<HologramCreationService.Creation> active =
          service.create("spawn/shutdown", "Shutdown", transform());
      assertTrue(awaitBlocked(worker, 5L, TimeUnit.SECONDS));
      service.stopAccepting();
      assertThrows(CancellationException.class,
          () -> service.create("spawn/rejected", "Rejected", transform()).join());
      Thread shutdown = new Thread(service::shutdown);
      shutdown.start();
      assertTrue(awaitBlocked(shutdown, 5L, TimeUnit.SECONDS));

      editorLease.close();
      shutdown.join(TimeUnit.SECONDS.toMillis(5L));

      assertFalse(shutdown.isAlive());
      assertEquals("spawn/shutdown", active.join().board().id());
    } finally {
      editorLease.close();
      service.shutdown();
    }
  }

  private static HologramCreationService service(
      Path data, MemoryMenus menus, MemoryBoards boards,
      HoloUiPersistenceCoordinator coordinator) {
    Logger logger = Logger.getAnonymousLogger();
    logger.setLevel(Level.OFF);
    logger.setUseParentHandlers(false);
    HologramCreationService.Dependencies dependencies =
        new HologramCreationService.Dependencies(
            data,
            logger,
            coordinator,
            new HoloUiProjectTransaction(data),
            menus,
            boards
        );
    ExecutorService executor = Executors.newSingleThreadExecutor();
    return new HologramCreationService(dependencies, executor);
  }

  private static BoardTransform transform() {
    return BoardTransform.at(
        "minecraft:overworld", UUID.randomUUID(), 1.0D, 64.0D, 2.0D, 30.0D);
  }

  private static boolean awaitBlocked(AtomicReference<Thread> threadReference, long timeout,
                                      TimeUnit unit) throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      Thread thread = threadReference.get();
      if (thread != null && blocked(thread)) {
        return true;
      }
      Thread.sleep(1L);
    }
    return false;
  }

  private static boolean awaitBlocked(Thread thread, long timeout, TimeUnit unit)
      throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (System.nanoTime() < deadline) {
      if (blocked(thread)) {
        return true;
      }
      Thread.sleep(1L);
    }
    return false;
  }

  private static boolean blocked(Thread thread) {
    return thread.getState() == Thread.State.WAITING
        || thread.getState() == Thread.State.TIMED_WAITING
        || thread.getState() == Thread.State.BLOCKED;
  }

  private static final class MemoryMenus implements HologramCreationService.MenuPublication {
    private final Path data;
    private final boolean failRecovery;
    private MenuDocument current;

    private MemoryMenus(Path data, boolean failRecovery) {
      this.data = data;
      this.failRecovery = failRecovery;
    }

    @Override
    public boolean exists(String id) {
      return current != null && current.id().equals(id);
    }

    @Override
    public MenuDocument publish(String id, String source) throws IOException {
      String persisted = Files.readString(
          data.resolve("menus").resolve(id + ".json"), StandardCharsets.UTF_8);
      current = MenuDocumentParser.parse(id, persisted);
      return current;
    }

    @Override
    public MenuDocument recover(MenuDocument created) throws IOException {
      if (failRecovery) {
        throw new IOException("simulated menu runtime recovery failure");
      }
      current = null;
      return created;
    }

    private Optional<MenuDocument> current() {
      return Optional.ofNullable(current);
    }
  }

  private static final class MemoryBoards implements HologramCreationService.BoardPublication {
    private final Path data;
    private final boolean failPublication;
    private final boolean failCommit;
    private final boolean corruptRollback;
    private BoardDefinition current;

    private MemoryBoards(Path data, boolean failPublication, boolean failCommit,
                         boolean corruptRollback) {
      this.data = data;
      this.failPublication = failPublication;
      this.failCommit = failCommit;
      this.corruptRollback = corruptRollback;
    }

    @Override
    public Optional<BoardDefinition> current(String id) {
      return current != null && current.id().equals(id)
          ? Optional.of(current)
          : Optional.empty();
    }

    @Override
    public BoardDefinition publish(BoardDefinition created) throws IOException {
      if (failPublication) {
        if (corruptRollback) {
          Files.writeString(data.resolve("menus").resolve(created.id() + ".json"),
              "{}", StandardCharsets.UTF_8);
        }
        throw new IOException("simulated board publication failure");
      }
      current = created;
      if (failCommit) {
        Path transactions = data.resolve("editor-sync-transactions");
        Path transaction;
        try (Stream<Path> paths = Files.list(transactions)) {
          transaction = paths.findFirst().orElseThrow();
        }
        Path journal = transaction.resolve("journal.json");
        JsonObject document = JsonParser.parseString(Files.readString(journal)).getAsJsonObject();
        document.addProperty("state", "publishing");
        Files.writeString(journal, document.toString(), StandardCharsets.UTF_8);
      }
      return created;
    }

    @Override
    public BoardDefinition recover(BoardDefinition created) {
      current = null;
      return created;
    }
  }
}
