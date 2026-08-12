package art.arcane.holoui.config.menu;

import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MenuMutationServiceTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void writesAreSerializedAndSecondStaleSnapshotConflicts() throws IOException {
    File pluginData = temp.newFolder("serialized");
    Path menu = writeMenu(pluginData);
    String revision = MenuDocument.revisionOf(Files.readString(menu));
    ManualTaskRunner runner = new ManualTaskRunner();
    MenuMutationService service = service(pluginData, runner);

    CompletableFuture<MenuDocument> first = service.mutate("shop", revision,
        document -> MenuRowMutations.setTextRow(document, 1, "First"));
    CompletableFuture<MenuDocument> second = service.mutate("shop", revision,
        document -> MenuRowMutations.setTextRow(document, 1, "Second"));

    assertEquals(1, runner.size());
    assertEquals("Original", text(Files.readString(menu)));
    runner.runNext();
    assertEquals("First", text(first.join().source()));
    assertEquals(1, runner.size());
    runner.runNext();
    assertTrue(assertThrows(CompletionException.class, second::join)
        .getCause() instanceof MenuRevisionConflictException);
  }

  @Test
  public void createValidatesAndNeverOverwrites() throws IOException {
    File pluginData = temp.newFolder("create");
    ManualTaskRunner runner = new ManualTaskRunner();
    MenuMutationService service = service(pluginData, runner);
    String source = """
        {
          "offset": [0, 0, 0],
          "components": []
        }
        """;

    CompletableFuture<MenuDocument> createdFuture = service.create("imports/gholo/welcome", source);
    runner.runNext();
    MenuDocument created = createdFuture.join();
    assertEquals("imports/gholo/welcome", created.id());
    assertTrue(Files.isRegularFile(pluginData.toPath()
        .resolve("menus/imports/gholo/welcome.json")));

    CompletableFuture<MenuDocument> duplicate = service.create("imports/gholo/welcome", source);
    runner.runNext();
    assertTrue(assertThrows(CompletionException.class, duplicate::join)
        .getCause() instanceof FileAlreadyExistsException);

    CompletableFuture<MenuDocument> malformed = service.create("imports/gholo/malformed", "[]");
    runner.runNext();
    assertTrue(assertThrows(CompletionException.class, malformed::join)
        .getCause() instanceof IllegalArgumentException);
    assertFalse(Files.exists(pluginData.toPath()
        .resolve("menus/imports/gholo/malformed.json")));
  }

  @Test
  public void shutdownCancelsActiveAndQueuedWrites() throws IOException {
    File pluginData = temp.newFolder("shutdown");
    Path menu = writeMenu(pluginData);
    String revision = MenuDocument.revisionOf(Files.readString(menu));
    ManualTaskRunner runner = new ManualTaskRunner();
    MenuMutationService service = service(pluginData, runner);
    CompletableFuture<MenuDocument> active = service.mutate("shop", revision, document -> document);
    CompletableFuture<MenuDocument> queued = service.mutate("shop", revision, document -> document);

    service.shutdown();

    assertFalse(service.isRunning());
    assertThrows(CancellationException.class, active::join);
    assertThrows(CancellationException.class, queued::join);
    runner.runAll();
    assertThrows(CancellationException.class,
        () -> service.mutate("shop", revision, document -> document).join());
  }

  @Test
  public void shutdownWaitsForAnActiveWriteWithoutInterruptingIt() throws Exception {
    File pluginData = temp.newFolder("active-shutdown");
    Path menu = writeMenu(pluginData);
    String revision = MenuDocument.revisionOf(Files.readString(menu));
    Logger logger = Logger.getLogger(MenuMutationServiceTest.class.getName() + ".active-shutdown");
    logger.setLevel(Level.OFF);
    MenuMutationService service = new MenuMutationService(new MenuMutationService.Dependencies(
        new MenuDocumentRepository(pluginData),
        new MenuExecutorTaskRunner(MenuMutationServiceTest.class.getClassLoader()),
        logger, new HoloUiPersistenceCoordinator()));
    CountDownLatch mutationStarted = new CountDownLatch(1);
    CountDownLatch allowMutation = new CountDownLatch(1);
    AtomicBoolean interrupted = new AtomicBoolean();
    CompletableFuture<MenuDocument> write = service.mutate("shop", revision, document -> {
      mutationStarted.countDown();
      try {
        allowMutation.await();
      } catch (InterruptedException interruption) {
        interrupted.set(true);
        Thread.currentThread().interrupt();
      }
      return MenuRowMutations.setTextRow(document, 1, "Finished");
    });
    assertTrue(mutationStarted.await(5L, TimeUnit.SECONDS));

    CountDownLatch shutdownEntered = new CountDownLatch(1);
    Thread shutdown = new Thread(() -> {
      shutdownEntered.countDown();
      service.shutdown();
    }, "menu-service-shutdown-test");
    shutdown.start();
    assertTrue(shutdownEntered.await(5L, TimeUnit.SECONDS));
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
    while (service.isRunning() && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertFalse(service.isRunning());
    assertTrue(shutdown.isAlive());
    allowMutation.countDown();
    shutdown.join(TimeUnit.SECONDS.toMillis(5L));

    assertFalse(shutdown.isAlive());
    assertFalse(interrupted.get());
    assertThrows(CancellationException.class, write::join);
    assertEquals("Finished", text(Files.readString(menu)));
  }

  private static MenuMutationService service(File pluginData, MenuTaskRunner runner) {
    Logger logger = Logger.getLogger(MenuMutationServiceTest.class.getName());
    logger.setLevel(Level.OFF);
    return new MenuMutationService(new MenuMutationService.Dependencies(
        new MenuDocumentRepository(pluginData), runner, logger, new HoloUiPersistenceCoordinator()));
  }

  private static Path writeMenu(File pluginData) throws IOException {
    Path path = pluginData.toPath().resolve("menus/shop.json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, """
        {
          "offset": [0, 0, 1],
          "components": [{
            "id": "row",
            "offset": [0, 0, 0],
            "data": {
              "type": "decoration",
              "icon": {"type": "text", "text": "Original"}
            }
          }]
        }
        """);
    return path;
  }

  private static String text(String source) {
    return JsonParser.parseString(source).getAsJsonObject()
        .getAsJsonArray("components").get(0).getAsJsonObject().getAsJsonObject("data")
        .getAsJsonObject("icon").get("text").getAsString();
  }

  private static final class ManualTaskRunner implements MenuTaskRunner {
    private final ArrayDeque<ManualTask> tasks = new ArrayDeque<>();

    @Override
    public MenuTaskHandle submit(Runnable task) {
      ManualTask scheduled = new ManualTask(task);
      tasks.addLast(scheduled);
      return scheduled::cancel;
    }

    private int size() {
      return tasks.size();
    }

    private void runNext() {
      tasks.removeFirst().run();
    }

    private void runAll() {
      while (!tasks.isEmpty()) {
        runNext();
      }
    }
  }

  private static final class ManualTask {
    private final Runnable task;
    private boolean cancelled;

    private ManualTask(Runnable task) {
      this.task = task;
    }

    private void cancel() {
      cancelled = true;
    }

    private void run() {
      if (!cancelled) {
        task.run();
      }
    }
  }
}
