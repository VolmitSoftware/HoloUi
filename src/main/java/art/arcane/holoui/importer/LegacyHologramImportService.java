package art.arcane.holoui.importer;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardService;
import art.arcane.holoui.config.ConfigManager;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.World;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class LegacyHologramImportService {
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;

  private final HoloUI plugin;
  private final ConfigManager configManager;
  private final Path pluginsDirectory;
  private final ExecutorService scannerExecutor;
  private final LegacyHologramScanner scanner;
  private final LegacyHologramConverter converter;
  private final LegacyImportApplyCoordinator applyCoordinator;
  private final AtomicBoolean accepting;
  private final AtomicBoolean busy;
  private final Set<CompletableFuture<?>> activeOperations;

  public LegacyHologramImportService(HoloUI plugin, ConfigManager configManager,
                                     File pluginDataDirectory) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.configManager = Objects.requireNonNull(configManager, "configManager");
    File dataDirectory = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory");
    File parent = Objects.requireNonNull(dataDirectory.getAbsoluteFile().getParentFile(),
        "plugin data directory must have a parent");
    this.pluginsDirectory = parent.toPath().toAbsolutePath().normalize();
    this.scannerExecutor = Executors.newSingleThreadExecutor(threadFactory(
        plugin.getClass().getClassLoader()));
    this.scanner = new LegacyHologramScanner();
    this.converter = new LegacyHologramConverter();
    this.applyCoordinator = new LegacyImportApplyCoordinator();
    this.accepting = new AtomicBoolean(true);
    this.busy = new AtomicBoolean();
    this.activeOperations = ConcurrentHashMap.newKeySet();
  }

  public CompletableFuture<LegacyImportPlan> preview(LegacyImportSource source) {
    return begin(source, false).thenApply(result -> (LegacyImportPlan) result);
  }

  public CompletableFuture<LegacyImportApplyResult> apply(LegacyImportSource source) {
    return begin(source, true).thenApply(result -> (LegacyImportApplyResult) result);
  }

  public boolean isBusy() {
    return busy.get();
  }

  public void shutdown() {
    if (!accepting.compareAndSet(true, false)) {
      return;
    }
    for (CompletableFuture<?> operation : activeOperations) {
      operation.completeExceptionally(new CancellationException(
          "legacy hologram import service is shutting down"));
    }
    scannerExecutor.shutdown();
    try {
      if (!scannerExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        scannerExecutor.shutdownNow();
        plugin.getLogger().warning(
            "Legacy hologram source scan exceeded the shutdown timeout and was interrupted.");
      }
    } catch (InterruptedException interruption) {
      scannerExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private CompletableFuture<Object> begin(LegacyImportSource source, boolean apply) {
    LegacyImportSource requiredSource = Objects.requireNonNull(source, "source");
    if (!accepting.get()) {
      return CompletableFuture.failedFuture(
          new CancellationException("legacy hologram import service is shut down"));
    }
    if (!busy.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(new LegacyImportBusyException());
    }
    CompletableFuture<Object> result = new CompletableFuture<>();
    activeOperations.add(result);
    result.whenComplete((ignored, failure) -> {
      activeOperations.remove(result);
      busy.set(false);
    });
    boolean scheduled = SchedulerUtils.runGlobal(plugin,
        () -> captureAndScan(requiredSource, apply, result));
    if (!scheduled) {
      result.completeExceptionally(
          new IllegalStateException("unable to schedule legacy import snapshot capture"));
    }
    return result;
  }

  private void captureAndScan(LegacyImportSource source, boolean apply,
                              CompletableFuture<Object> result) {
    if (!accepting.get() || result.isDone()) {
      result.completeExceptionally(new CancellationException(
          "legacy hologram import service stopped before source scan"));
      return;
    }
    try {
      LegacyWorldCatalog worlds = LegacyWorldCatalog.capture(plugin.getServer().getWorlds());
      Path sourcePath = sourcePath(source);
      CompletableFuture.supplyAsync(() -> scanner.scan(source, sourcePath, pluginsDirectory), scannerExecutor)
          .whenComplete((scan, failure) -> {
            if (failure != null) {
              result.completeExceptionally(failure);
              return;
            }
            boolean accepted = SchedulerUtils.runGlobal(plugin,
                () -> finishPlan(source, sourcePath, worlds, scan, apply, result));
            if (!accepted) {
              result.completeExceptionally(
                  new IllegalStateException("unable to schedule legacy import plan publication"));
            }
          });
    } catch (RuntimeException failure) {
      result.completeExceptionally(failure);
    }
  }

  private void finishPlan(LegacyImportSource source, Path sourcePath, LegacyWorldCatalog worlds,
                          LegacyHologramScanner.LegacyScanResult scan, boolean apply,
                          CompletableFuture<Object> result) {
    if (!accepting.get() || result.isDone()) {
      result.completeExceptionally(new CancellationException(
          "legacy hologram import service stopped during source scan"));
      return;
    }
    LegacyHologramConverter.ConversionResult conversion = converter.convert(source, scan.drafts(), worlds);
    List<LegacyImportIssue> issues = new ArrayList<>(scan.issues());
    issues.addAll(conversion.issues());
    ExistingSnapshot existing = snapshotExisting();
    List<LegacyImportCandidate> candidates = classify(
        conversion.candidates(), existing.menuSources(), existing.boardIds());
    LegacyImportPlan plan = new LegacyImportPlan(source, sourcePath.toString(), scan.sourcePresent(),
        candidates, issues);
    if (!apply) {
      result.complete(plan);
      return;
    }
    LegacyImportApplyCoordinator.ImportPublisher publisher = publisher();
    applyCoordinator.apply(plan, publisher).whenComplete((applied, failure) -> {
      if (failure == null) {
        result.complete(applied);
      } else {
        result.completeExceptionally(failure);
      }
    });
  }

  private ExistingSnapshot snapshotExisting() {
    Map<String, String> menuSources = new HashMap<>();
    for (String menuId : configManager.keys()) {
      configManager.getSource(menuId).ifPresent(source -> menuSources.put(menuId, source));
    }
    BoardService boardService = requireBoardService();
    Set<String> boardIds = new HashSet<>();
    for (BoardDefinition board : boardService.list()) {
      boardIds.add(board.id());
    }
    return new ExistingSnapshot(menuSources, boardIds);
  }

  static List<LegacyImportCandidate> classify(List<LegacyImportCandidate> candidates,
                                              Map<String, String> menuSources,
                                              Set<String> boardIds) {
    Objects.requireNonNull(candidates, "candidates");
    Objects.requireNonNull(menuSources, "menuSources");
    Objects.requireNonNull(boardIds, "boardIds");
    List<LegacyImportCandidate> classified = new ArrayList<>(candidates.size());
    for (LegacyImportCandidate candidate : candidates) {
      if (candidate.disposition() == LegacyImportDisposition.CONFLICT) {
        classified.add(candidate);
        continue;
      }
      boolean boardExists = boardIds.contains(candidate.boardId());
      String menuSource = menuSources.get(candidate.menuId());
      if (boardExists) {
        classified.add(candidate.withDisposition(LegacyImportDisposition.CONFLICT,
            "target board already exists"));
      } else if (menuSource == null) {
        classified.add(candidate);
      } else if (menuSource.equals(candidate.menuSource())) {
        classified.add(candidate.withDisposition(LegacyImportDisposition.RESUME_BOARD,
            "matching menu exists without its board"));
      } else {
        classified.add(candidate.withDisposition(LegacyImportDisposition.CONFLICT,
            "target menu already exists with different content"));
      }
    }
    return List.copyOf(classified);
  }

  private LegacyImportApplyCoordinator.ImportPublisher publisher() {
    return new LegacyImportApplyCoordinator.ImportPublisher() {
      @Override
      public CompletableFuture<Void> createMenu(LegacyImportCandidate candidate) {
        if (!accepting.get()) {
          return CompletableFuture.failedFuture(new CancellationException(
              "legacy hologram import service is shutting down"));
        }
        return configManager.createMenu(candidate.menuId(), candidate.menuSource())
            .thenApply(document -> null);
      }

      @Override
      public CompletableFuture<Void> createBoard(LegacyImportCandidate candidate) {
        if (!accepting.get()) {
          return CompletableFuture.failedFuture(new CancellationException(
              "legacy hologram import service is shutting down"));
        }
        return requireBoardService().create(candidate.board()).thenApply(board -> null);
      }
    };
  }

  private BoardService requireBoardService() {
    BoardService boardService = plugin.getBoardService();
    if (boardService == null || !boardService.isRunning()) {
      throw new IllegalStateException("persistent board service is not running");
    }
    return boardService;
  }

  private Path sourcePath(LegacyImportSource source) {
    Path relative = switch (source) {
      case GHOLO -> Path.of("GHolo", "holos");
      case DECENT_HOLOGRAMS -> Path.of("DecentHolograms", "holograms");
      case HOLOGRAPHIC_DISPLAYS -> Path.of("HolographicDisplays", "database.yml");
      case FANCY_HOLOGRAMS -> Path.of("FancyHolograms", "holograms.yml");
    };
    Path target = pluginsDirectory.resolve(relative).normalize();
    return target;
  }

  private static ThreadFactory threadFactory(ClassLoader classLoader) {
    return task -> {
      Thread thread = new Thread(task, "HoloUI-Legacy-Hologram-Scanner");
      thread.setDaemon(true);
      thread.setContextClassLoader(classLoader);
      thread.setUncaughtExceptionHandler((failedThread, failure) ->
          Logger.getLogger("HoloUi").severe("Legacy hologram scanner failed: " + failure));
      return thread;
    };
  }

  private record ExistingSnapshot(Map<String, String> menuSources, Set<String> boardIds) {
    private ExistingSnapshot {
      menuSources = Map.copyOf(menuSources);
      boardIds = Set.copyOf(boardIds);
    }
  }
}
