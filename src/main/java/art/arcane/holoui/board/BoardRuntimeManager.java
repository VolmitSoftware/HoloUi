package art.arcane.holoui.board;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.action.NavigationResult;
import art.arcane.holoui.menu.components.ClickableComponent;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.volmlib.util.bukkit.Events;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class BoardRuntimeManager implements BoardServiceListener {
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;

  private final HoloUI plugin;
  private final BoardService boards;
  private final BoardSpatialIndex effectiveIndex = new BoardSpatialIndex();
  private final Map<UUID, BoardDefinition> definitions = new ConcurrentHashMap<>();
  private final Map<UUID, ViewerState> viewers = new ConcurrentHashMap<>();
  private final Map<UUID, BoardPreview> previews = new ConcurrentHashMap<>();
  private final Set<UUID> tickingViewers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> samplingTargets = ConcurrentHashMap.newKeySet();
  private final Map<UUID, BoardFollowPose> followPoses = new ConcurrentHashMap<>();
  private final AtomicInteger visibleBoards = new AtomicInteger();
  private final SchedulerUtils.TaskHandle tickTask;

  private volatile boolean running = true;

  public BoardRuntimeManager(HoloUI plugin, BoardService boards) {
    this.plugin = plugin;
    this.boards = boards;
    replaceDefinitions(boards.subscribeAndSnapshot(this));
    Events.listen(plugin, PlayerQuitEvent.class, event -> closeViewer(event.getPlayer()));
    this.tickTask = SchedulerUtils.scheduleSyncTask(plugin, 1L, this::scheduleTick, false);
  }

  public int visibleBoardCount() {
    return visibleBoards.get();
  }

  public Optional<BoardDefinition> effectiveBoard(UUID boardUuid) {
    return effectiveIndex.get(Objects.requireNonNull(boardUuid, "boardUuid"));
  }

  public List<BoardDefinition> queryEffective(UUID worldUuid, double x, double z, double radius) {
    return effectiveIndex.query(worldUuid, x, z, radius);
  }

  public Optional<BoardFollowPose> followPose(UUID targetPlayerUuid) {
    return Optional.ofNullable(followPoses.get(Objects.requireNonNull(targetPlayerUuid, "targetPlayerUuid")));
  }

  public BoardClickTarget findClickTarget(Player player) {
    if (!running || player == null || !player.isOnline()) {
      return null;
    }
    ViewerState state = viewers.get(player.getUniqueId());
    return state == null ? null : state.findClickTarget();
  }

  public void previewBoard(Player viewer, BoardDefinition definition) {
    BoardDefinition requiredDefinition = Objects.requireNonNull(definition, "definition");
    BoardTransform effectiveTransform = requiredDefinition.transform();
    if (requiredDefinition.follow().mode() == BoardFollowMode.PLAYER) {
      effectiveTransform = effectiveIndex.get(requiredDefinition.uuid())
          .map(BoardDefinition::transform)
          .orElse(effectiveTransform);
    }
    previewBoard(viewer, requiredDefinition, effectiveTransform);
  }

  public void previewBoard(Player viewer, BoardDefinition definition, BoardTransform effectiveTransform) {
    Player requiredViewer = Objects.requireNonNull(viewer, "viewer");
    BoardPreview preview = new BoardPreview(definition, effectiveTransform);
    previews.put(requiredViewer.getUniqueId(), preview);
  }

  public void clearBoardPreview(Player viewer, UUID boardUuid) {
    Player requiredViewer = Objects.requireNonNull(viewer, "viewer");
    UUID requiredBoardUuid = Objects.requireNonNull(boardUuid, "boardUuid");
    previews.computeIfPresent(requiredViewer.getUniqueId(), (ignored, preview) ->
        preview.definition().uuid().equals(requiredBoardUuid) ? null : preview);
  }

  public void refreshMenu(String menuId) {
    for (ViewerState state : viewers.values()) {
      Runnable refresh = () -> state.refreshMenu(menuId);
      SchedulerUtils.runEntity(plugin, state.player, refresh);
    }
  }

  public void refreshVisuals() {
    for (ViewerState state : viewers.values()) {
      Runnable refresh = state::refreshVisuals;
      SchedulerUtils.runEntity(plugin, state.player, refresh);
    }
  }

  public void shutdown() {
    if (!running) {
      return;
    }
    running = false;
    boards.removeListener(this);
    tickTask.cancel();
    closeViewersForShutdown();
    viewers.clear();
    previews.clear();
    tickingViewers.clear();
    samplingTargets.clear();
    followPoses.clear();
    definitions.clear();
    effectiveIndex.replaceAll(List.of());
    visibleBoards.set(0);
  }

  @Override
  public void boardCreated(BoardDefinition board) {
    publishDefinition(board);
  }

  @Override
  public void boardUpdated(BoardDefinition previous, BoardDefinition updated) {
    publishDefinition(updated);
  }

  @Override
  public void boardDeleted(BoardDefinition board) {
    definitions.remove(board.uuid());
    effectiveIndex.remove(board.uuid());
    if (board.follow().targetPlayerUuid() != null) {
      removeUnusedFollowPose(board.follow().targetPlayerUuid());
    }
    previews.entrySet().removeIf(entry -> entry.getValue().definition().uuid().equals(board.uuid()));
  }

  @Override
  public void boardsReloaded(BoardLoadResult result, List<BoardDefinition> loadedBoards) {
    replaceDefinitions(loadedBoards);
  }

  private void scheduleTick() {
    if (!running) {
      return;
    }
    sampleFollowTargets();
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID playerId = player.getUniqueId();
      if (!tickingViewers.add(playerId)) {
        continue;
      }
      Runnable tick = () -> {
        try {
          if (!running || !player.isOnline()) {
            closeViewer(player);
            return;
          }
          viewers.computeIfAbsent(playerId, ignored -> new ViewerState(player)).tick();
        } catch (RuntimeException failure) {
          HoloUI.logExceptionStack(false, failure, "Failed to update persistent boards for %s.", player.getName());
          closeViewer(player);
        } finally {
          tickingViewers.remove(playerId);
        }
      };
      Runnable retired = () -> tickingViewers.remove(playerId);
      if (!FoliaScheduler.runEntity(plugin, player, tick, 0L, retired)) {
        tickingViewers.remove(playerId);
      }
    }
  }

  private void sampleFollowTargets() {
    Map<UUID, List<BoardDefinition>> byTarget = new HashMap<>();
    for (BoardDefinition board : definitions.values()) {
      if (board.follow().mode() != BoardFollowMode.PLAYER) {
        continue;
      }
      byTarget.computeIfAbsent(board.follow().targetPlayerUuid(), ignored -> new ArrayList<>()).add(board);
    }

    for (Map.Entry<UUID, List<BoardDefinition>> entry : byTarget.entrySet()) {
      UUID targetId = entry.getKey();
      Player target = Bukkit.getPlayer(targetId);
      if (target == null || !target.isOnline()) {
        continue;
      }
      if (!samplingTargets.add(targetId)) {
        continue;
      }
      List<BoardDefinition> snapshot = List.copyOf(entry.getValue());
      Runnable sample = () -> {
        try {
          if (!running || !target.isOnline()) {
            return;
          }
          Location targetLocation = target.getLocation();
          BoardFollowPose targetPose = BoardFollowPose.from(targetLocation);
          followPoses.put(targetId, targetPose);
          for (BoardDefinition sampled : snapshot) {
            BoardDefinition current = definitions.get(sampled.uuid());
            if (current == null || current.revision() != sampled.revision()
                || current.follow().mode() != BoardFollowMode.PLAYER) {
              continue;
            }
            BoardTransform transform = BoardFollowTransform.resolve(current, targetPose);
            effectiveIndex.upsert(current.withTransform(transform));
          }
        } catch (RuntimeException failure) {
          HoloUI.logExceptionStack(false, failure, "Failed to update boards following %s.", target.getName());
        } finally {
          samplingTargets.remove(targetId);
        }
      };
      Runnable retired = () -> samplingTargets.remove(targetId);
      if (!FoliaScheduler.runEntity(plugin, target, sample, 0L, retired)) {
        samplingTargets.remove(targetId);
      }
    }
  }

  private void publishDefinition(BoardDefinition board) {
    BoardDefinition previous = definitions.put(board.uuid(), board);
    if (previous != null && previous.follow().targetPlayerUuid() != null
        && !Objects.equals(previous.follow().targetPlayerUuid(), board.follow().targetPlayerUuid())) {
      removeUnusedFollowPose(previous.follow().targetPlayerUuid());
    }
    if (board.follow().mode() == BoardFollowMode.NONE) {
      effectiveIndex.upsert(board);
    } else {
      BoardFollowPose pose = followPoses.get(board.follow().targetPlayerUuid());
      if (pose == null) {
        effectiveIndex.remove(board.uuid());
      } else {
        effectiveIndex.upsert(board.withTransform(BoardFollowTransform.resolve(board, pose)));
      }
    }
  }

  private void replaceDefinitions(List<BoardDefinition> loadedBoards) {
    definitions.clear();
    List<BoardDefinition> effectiveBoards = new ArrayList<>(loadedBoards.size());
    Set<UUID> activeFollowTargets = new HashSet<>();
    for (BoardDefinition board : loadedBoards) {
      definitions.put(board.uuid(), board);
      if (board.follow().mode() == BoardFollowMode.NONE) {
        effectiveBoards.add(board);
        continue;
      }
      activeFollowTargets.add(board.follow().targetPlayerUuid());
      BoardFollowPose pose = followPoses.get(board.follow().targetPlayerUuid());
      if (pose != null) {
        effectiveBoards.add(board.withTransform(BoardFollowTransform.resolve(board, pose)));
      }
    }
    effectiveIndex.replaceAll(effectiveBoards);
    followPoses.keySet().removeIf(targetId -> !activeFollowTargets.contains(targetId));
  }

  private void removeUnusedFollowPose(UUID targetId) {
    boolean used = definitions.values().stream()
        .anyMatch(board -> targetId.equals(board.follow().targetPlayerUuid()));
    if (!used) {
      followPoses.remove(targetId);
    }
  }

  private void closeViewer(Player player) {
    ViewerState state = viewers.remove(player.getUniqueId());
    previews.remove(player.getUniqueId());
    if (state != null) {
      state.close();
    }
  }

  private void closeViewersForShutdown() {
    List<ViewerState> snapshot = List.copyOf(viewers.values());
    CountDownLatch closed = new CountDownLatch(snapshot.size());
    for (ViewerState state : snapshot) {
      AtomicBoolean completed = new AtomicBoolean();
      Runnable close = () -> {
        if (!completed.compareAndSet(false, true)) {
          return;
        }
        try {
          state.close();
          viewers.remove(state.player.getUniqueId(), state);
        } catch (RuntimeException failure) {
          HoloUI.logExceptionStack(false, failure,
              "Failed to close persistent board views for %s during shutdown.", state.player.getName());
        } finally {
          tickingViewers.remove(state.player.getUniqueId());
          closed.countDown();
        }
      };
      if (!FoliaScheduler.runEntity(plugin, state.player, close, 0L, close)) {
        close.run();
      }
    }
    boolean interrupted = false;
    while (closed.getCount() > 0L) {
      try {
        if (!closed.await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          HoloUI.log(java.util.logging.Level.WARNING,
              "Still waiting for persistent board viewer tasks to close during shutdown.");
        }
      } catch (InterruptedException interruption) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private boolean canView(Player player, BoardDefinition board) {
    BoardVisibility visibility = board.visibility();
    return switch (visibility.mode()) {
      case PUBLIC -> true;
      case PERMISSION -> player.hasPermission(visibility.viewPermission());
      case HIDDEN -> false;
    };
  }

  private boolean canInteract(Player player, BoardDefinition board) {
    String permission = board.visibility().interactPermission();
    return permission == null || player.hasPermission(permission);
  }

  private final class ViewerState {
    private final Player player;
    private final Map<UUID, BoardViewSession> views = new HashMap<>();
    private final Map<UUID, Long> unavailable = new HashMap<>();
    private final Set<UUID> dismissed = new HashSet<>();
    private boolean closed;

    private ViewerState(Player player) {
      this.player = player;
    }

    private synchronized void tick() {
      if (closed || !running) {
        close();
        return;
      }
      Location location = player.getLocation();
      World world = location.getWorld();
      if (world == null) {
        closeViews();
        return;
      }

      double queryRange = boards.maximumViewRange();
      List<BoardDefinition> candidates = queryRange <= 0.0D
          ? List.of()
          : effectiveIndex.query(world.getUID(), location.getX(), location.getZ(), queryRange);
      BoardPreview preview = previews.get(player.getUniqueId());
      Map<UUID, BoardDefinition> effectiveCandidates = new LinkedHashMap<>(candidates.size() + 1);
      for (BoardDefinition candidate : candidates) {
        effectiveCandidates.put(candidate.uuid(), candidate);
      }
      if (preview != null) {
        BoardTransform previewTransform = preview.effectiveTransform();
        BoardDefinition previewDefinition = preview.definition();
        if (previewDefinition.follow().mode() == BoardFollowMode.PLAYER) {
          BoardFollowPose pose = followPoses.get(previewDefinition.follow().targetPlayerUuid());
          if (pose != null) {
            previewTransform = BoardFollowTransform.resolve(previewDefinition, pose);
          }
        }
        effectiveCandidates.put(previewDefinition.uuid(), previewDefinition.withTransform(previewTransform));
      }
      Set<UUID> inRange = new HashSet<>();
      for (BoardDefinition effective : effectiveCandidates.values()) {
        boolean editing = preview != null && preview.definition().uuid().equals(effective.uuid());
        BoardDefinition definition = editing ? preview.definition() : definitions.get(effective.uuid());
        if (definition == null
            || !effective.transform().worldUuid().equals(world.getUID())
            || (!editing && !canView(player, definition))) {
          continue;
        }
        double range = definition.visibility().viewRange();
        if (!editing && BoardPlacement.distanceSquared(effective, location) > range * range) {
          continue;
        }
        inRange.add(definition.uuid());
        if (editing) {
          dismissed.remove(definition.uuid());
          unavailable.remove(definition.uuid());
        }
        if (dismissed.contains(definition.uuid())) {
          continue;
        }
        Long failedRevision = unavailable.get(definition.uuid());
        if (failedRevision != null && failedRevision == definition.revision()) {
          continue;
        }
        unavailable.remove(definition.uuid());
        BoardViewSession view = views.get(definition.uuid());
        if (view == null) {
          view = new BoardViewSession(new BoardViewOptions(
              definition,
              effective.transform(),
              player,
              plugin.getConfigManager(),
              this::dismiss
          ));
          NavigationResult openResult = view.open();
          if (openResult != NavigationResult.APPLIED) {
            view.close();
            if (openResult == NavigationResult.NOT_FOUND) {
              unavailable.put(definition.uuid(), definition.revision());
            }
            continue;
          }
          views.put(definition.uuid(), view);
          visibleBoards.incrementAndGet();
        } else {
          NavigationResult updateResult = view.update(definition, effective.transform());
          if (updateResult != NavigationResult.APPLIED) {
            views.remove(definition.uuid(), view);
            visibleBoards.decrementAndGet();
            if (updateResult == NavigationResult.NOT_FOUND) {
              unavailable.put(definition.uuid(), definition.revision());
            }
            continue;
          }
        }
        view.tick();
      }

      Iterator<Map.Entry<UUID, BoardViewSession>> viewIterator = views.entrySet().iterator();
      while (viewIterator.hasNext()) {
        Map.Entry<UUID, BoardViewSession> entry = viewIterator.next();
        if (!inRange.contains(entry.getKey())) {
          entry.getValue().close();
          viewIterator.remove();
          visibleBoards.decrementAndGet();
        }
      }
      dismissed.removeIf(boardId -> !inRange.contains(boardId));
      unavailable.keySet().removeIf(boardId -> !inRange.contains(boardId));
    }

    private synchronized BoardClickTarget findClickTarget() {
      Location eye = player.getEyeLocation();
      Vector origin = eye.toVector();
      Vector direction = eye.getDirection();
      BoardClickTarget nearest = null;
      BoardPreview preview = previews.get(player.getUniqueId());
      for (BoardViewSession view : views.values()) {
        boolean editing = preview != null && preview.definition().uuid().equals(view.definition().uuid());
        BoardDefinition definition = editing
            ? preview.definition()
            : definitions.get(view.definition().uuid());
        if (definition == null || definition.revision() != view.definition().revision()) {
          continue;
        }
        double interactionRange = editing
            ? Double.POSITIVE_INFINITY
            : definition.visibility().interactionRange();
        BoardTransform currentEffective = editing
            ? (definition.follow().mode() == BoardFollowMode.PLAYER
                ? followPose(definition.follow().targetPlayerUuid())
                    .map(pose -> BoardFollowTransform.resolve(preview.definition(), pose))
                    .orElse(preview.effectiveTransform())
                : preview.effectiveTransform())
            : effectiveIndex.get(definition.uuid()).map(BoardDefinition::transform).orElse(null);
        if ((!editing && (!canView(player, definition) || !canInteract(player, definition)))
            || currentEffective == null
            || !view.effectiveTransform().equals(currentEffective)
            || BoardPlacement.distanceSquared(view.effectiveTransform(), eye) > interactionRange * interactionRange) {
          continue;
        }
        MenuSession session = view.session();
        if (session == null) {
          continue;
        }
        for (MenuComponent<?> component : session.getComponents()) {
          if (!(component instanceof ClickableComponent<?> clickable)) {
            continue;
          }
          OptionalDouble distance = clickable.intersectionDistance(origin, direction);
          if (distance.isEmpty() || distance.getAsDouble() > interactionRange) {
            continue;
          }
          if (nearest == null || distance.getAsDouble() < nearest.distance()) {
            nearest = new BoardClickTarget(view, clickable, distance.getAsDouble());
          }
        }
      }
      return nearest;
    }

    private void dismiss(BoardViewSession view) {
      dismissed.add(view.definition().uuid());
      closeView(view.definition().uuid());
    }

    private synchronized void refreshMenu(String menuId) {
      unavailable.clear();
      for (BoardViewSession view : List.copyOf(views.values())) {
        if (view.currentMenuId() != null && view.currentMenuId().equals(menuId)) {
          boolean refreshed = plugin.getConfigManager().exists(view.currentMenuId())
              ? view.reloadCurrent()
              : view.returnHome();
          if (!refreshed) {
            UUID boardId = view.definition().uuid();
            unavailable.put(boardId, view.definition().revision());
            closeView(boardId);
          }
        }
      }
    }

    private synchronized void refreshVisuals() {
      for (BoardViewSession view : views.values()) {
        MenuSession session = view.session();
        if (session == null) {
          continue;
        }
        List<MenuComponent<?>> openComponents = session.getComponents().stream()
            .filter(MenuComponent::isOpen)
            .toList();
        openComponents.forEach(MenuComponent::close);
        session.refreshScale();
        openComponents.forEach(MenuComponent::open);
      }
    }

    private synchronized void close() {
      if (closed) {
        return;
      }
      closed = true;
      closeViews();
      dismissed.clear();
      unavailable.clear();
    }

    private void closeViews() {
      int count = views.size();
      for (BoardViewSession view : views.values()) {
        view.close();
      }
      views.clear();
      if (count > 0) {
        visibleBoards.addAndGet(-count);
      }
    }

    private void closeView(UUID boardId) {
      BoardViewSession view = views.remove(boardId);
      if (view != null) {
        view.close();
        visibleBoards.decrementAndGet();
      }
    }
  }

  private record BoardPreview(BoardDefinition definition, BoardTransform effectiveTransform) {
    private BoardPreview {
      definition = Objects.requireNonNull(definition, "definition");
      effectiveTransform = Objects.requireNonNull(effectiveTransform, "effectiveTransform");
      if (definition.follow().mode() != BoardFollowMode.PLAYER
          && !definition.transform().worldUuid().equals(effectiveTransform.worldUuid())) {
        throw new IllegalArgumentException("preview definition and effective transform must share a world");
      }
    }
  }
}
