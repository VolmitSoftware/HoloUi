package art.arcane.holoui.menu;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.enums.NavigationMode;
import art.arcane.holoui.menu.action.NavigationRequest;
import art.arcane.holoui.menu.action.NavigationResult;
import art.arcane.holoui.menu.components.ClickableComponent;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.special.inventories.ContainerPreview;
import art.arcane.holoui.service.HoloUiTelemetry;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import lombok.Synchronized;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

class SessionHolder {
  private final Object sessionLock = new Object();
  private final Object previewLock = new Object();

  private final Player player;
  private final UUID playerId;
  private final PlayerSnapshotStore<String> openMenus;
  private final Deque<String> navigationHistory = new ArrayDeque<>();
  private transient volatile MenuSession session;
  private transient ContainerPreview preview;
  private String navigationRoot;

  SessionHolder(Player player, PlayerSnapshotStore<String> openMenus) {
    this.player = player;
    this.playerId = player.getUniqueId();
    this.openMenus = openMenus;
  }

  void openSession(MenuDefinitionData data, ApiMenuHandle handle) {
    Opened opened = openSessionLocked(data, handle, NavigationMode.PUSH);
    settle(opened);
  }

  NavigationResult navigateSession(MenuDefinitionData data, NavigationRequest request) {
    Opened opened;
    synchronized (sessionLock) {
      if (!matchesNavigationTarget(data, request.mode())) {
        return NavigationResult.NO_HISTORY;
      }
      opened = openSessionLocked(data, null, request.mode());
    }
    settle(opened);
    return opened.rejected() == null && opened.failure() == null
        ? NavigationResult.APPLIED
        : NavigationResult.DENIED;
  }

  private static void settle(Opened opened) {
    if (opened.replaced() != null) {
      opened.replaced().terminate(HoloCloseReason.REPLACED);
    }
    if (opened.rejected() != null) {
      opened.rejected().terminate(opened.failure() == null ? HoloCloseReason.QUIT : HoloCloseReason.OPEN_FAILED);
    }
    if (opened.failure() instanceof RuntimeException failure) {
      throw failure;
    }
    if (opened.failure() instanceof Error failure) {
      throw failure;
    }
  }

  @Synchronized("previewLock")
  void openPreview(ContainerPreview session) {
    closePreview();
    preview = session;
    HoloUiTelemetry.incrementPreviewsOpen();
    preview.open();
  }

  boolean tick() {
    if (!player.isOnline()) {
      closeSession(false, HoloCloseReason.QUIT);
      closePreview();
      return true;
    }


    synchronized (sessionLock) {
      if (session != null) {
        session.tick();
      }
    }

    synchronized (previewLock) {
      if (preview != null) {
        try {
          if (!preview.tick()) {
            safelyClosePreview();
          }
        } catch (Exception ex) {
          HoloUI.logExceptionStack(false, ex, "Failed to tick preview for %s. Closing preview.", player.getName());
          safelyClosePreview();
        }
      }
    }
    return false;
  }

  boolean closeSession(boolean history, HoloCloseReason reason) {
    Detached detached = detachSession(history);
    if (detached == null) return false;
    if (detached.handle() != null) {
      detached.handle().terminate(reason);
    }
    return true;
  }

  boolean inspectSession(Function<@Nullable MenuSession, HoloCloseReason> action) {
    Closed closed = inspectSessionLocked(action);
    if (closed == null) return false;
    if (closed.handle() != null) {
      closed.handle().terminate(closed.reason());
    }
    return true;
  }

  boolean closeSessionOf(ApiMenuHandle handle, HoloCloseReason reason) {
    Detached detached = detachSessionOf(handle);
    if (detached == null) return false;
    handle.terminate(reason);
    return true;
  }

  @Synchronized("previewLock")
  void closePreview() {
    if (preview == null) return;
    safelyClosePreview();
  }

  void close(HoloCloseReason reason) {
    closeSession(false, reason);
    closePreview();
  }

  private void safelyClosePreview() {
    ContainerPreview current = preview;
    preview = null;
    if (current == null) {
      return;
    }

    HoloUiTelemetry.decrementPreviewsOpen();
    try {
      current.close();
    } catch (Exception ex) {
      HoloUI.logExceptionStack(false, ex, "Failed to close preview cleanly for %s.", player.getName());
    }
  }

  @Synchronized("sessionLock")
  void onSession(Consumer<@Nullable MenuSession> action) {
    action.accept(session);
  }

  boolean hasSession() {
    return session != null;
  }

  @Synchronized("sessionLock")
  boolean moveSession(Location anchor) {
    if (session == null) {
      return false;
    }
    session.move(anchor);
    return true;
  }

  ClickSnapshot snapshotClick(Location eyeLocation) {
    MenuSession current = session;
    if (current == null) return null;

    ClickableComponent<?> nearest = null;
    double nearestDistance = Double.POSITIVE_INFINITY;
    for (MenuComponent<?> component : current.getComponents()) {
      if (!(component instanceof ClickableComponent<?> clickable)) {
        continue;
      }
      OptionalDouble distance = clickable.intersectionDistance(
          eyeLocation.toVector(),
          eyeLocation.getDirection()
      );
      if (distance.isPresent() && distance.getAsDouble() < nearestDistance) {
        nearest = clickable;
        nearestDistance = distance.getAsDouble();
      }
    }

    if (nearest == null) return null;
    return new ClickSnapshot(current.getId(), current.getApiHandle(), nearest, nearestDistance);
  }

  @Synchronized("previewLock")
  void onPreview(Consumer<@Nullable ContainerPreview> action) {
    action.accept(preview);
  }

  @Synchronized("previewLock")
  boolean hasPreview() {
    return preview != null;
  }

  String lastSessionId() {
    synchronized (sessionLock) {
      return navigationHistory.peekFirst();
    }
  }

  String rootSessionId() {
    synchronized (sessionLock) {
      return navigationRoot;
    }
  }

  void refreshVisuals() {
    synchronized (sessionLock) {
      if (session != null) {
        List<MenuComponent<?>> openComponents = session.getComponents().stream()
            .filter(MenuComponent::isOpen)
            .toList();
        openComponents.forEach(MenuComponent::close);
        session.refreshScale();
        openComponents.forEach(MenuComponent::open);
      }
    }
    synchronized (previewLock) {
      if (preview != null) {
        preview.refreshVisuals();
      }
    }
  }

  @Synchronized("sessionLock")
  private Opened openSessionLocked(MenuDefinitionData data, ApiMenuHandle handle, NavigationMode mode) {
    if (!player.isOnline()) return new Opened(null, handle, null);

    MenuSession previous = session;
    String previousMenuId = previous == null ? null : previous.getId();
    openMenus.publish(playerId, data.getId());
    MenuSession replacement = null;
    try {
      replacement = new MenuSession(data, player, MenuSessionOptions.personal(data, player, handle));
      replacement.open();
    } catch (RuntimeException | Error failure) {
      if (replacement != null) {
        try {
          replacement.close();
        } catch (RuntimeException | Error cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      session = previous;
      openMenus.publish(playerId, previousMenuId);
      return new Opened(null, handle, failure);
    }

    commitNavigation(mode, data.getId(), previousMenuId);
    session = replacement;
    if (handle != null) {
      handle.markOpen();
    }
    HoloUiTelemetry.incrementMenusOpen();
    ApiMenuHandle replacedHandle = previous == null ? null : previous.getApiHandle();
    if (previous != null) {
      try {
        previous.close();
      } catch (RuntimeException | Error failure) {
        HoloUI.logExceptionStack(false, failure, "Failed to close replaced menu %s for %s.",
            previous.getId(), player.getName());
      } finally {
        HoloUiTelemetry.decrementMenusOpen();
      }
    }
    return new Opened(replacedHandle, null, null);
  }

  @Synchronized("sessionLock")
  private Detached detachSession(boolean history) {
    if (session == null) return null;
    if (history) {
      if (navigationRoot == null) {
        navigationRoot = session.getId();
      }
      navigationHistory.addFirst(session.getId());
    } else {
      clearNavigation();
    }
    return detachCurrentSession();
  }

  private void commitNavigation(NavigationMode mode, String targetId, String previousMenuId) {
    if (previousMenuId == null) {
      if (mode == NavigationMode.PUSH) {
        navigationHistory.clear();
        navigationRoot = targetId;
      } else if (mode == NavigationMode.REPLACE && navigationRoot == null) {
        navigationRoot = targetId;
      } else if (mode == NavigationMode.BACK) {
        navigationHistory.pollFirst();
      } else if (mode == NavigationMode.HOME) {
        navigationHistory.clear();
      }
      return;
    }

    if (navigationRoot == null) {
      navigationRoot = previousMenuId;
    }
    if (mode == NavigationMode.PUSH) {
      navigationHistory.addFirst(previousMenuId);
    } else if (mode == NavigationMode.BACK) {
      navigationHistory.pollFirst();
    } else if (mode == NavigationMode.HOME) {
      navigationHistory.clear();
    }
  }

  private Detached detachCurrentSession() {
    if (session == null) return null;
    ApiMenuHandle handle = session.getApiHandle();
    session.close();
    session = null;
    openMenus.publish(playerId, null);
    HoloUiTelemetry.decrementMenusOpen();
    return new Detached(handle);
  }

  private boolean matchesNavigationTarget(MenuDefinitionData data, NavigationMode mode) {
    if (data == null) {
      return false;
    }
    return switch (mode) {
      case PUSH, REPLACE -> true;
      case BACK -> Objects.equals(navigationHistory.peekFirst(), data.getId());
      case HOME -> Objects.equals(navigationRoot, data.getId());
      case CLOSE -> false;
    };
  }

  private void clearNavigation() {
    navigationHistory.clear();
    navigationRoot = null;
  }

  @Synchronized("sessionLock")
  private Detached detachSessionOf(ApiMenuHandle handle) {
    if (session == null || session.getApiHandle() != handle) return null;
    return detachSession(false);
  }

  @Synchronized("sessionLock")
  private Closed inspectSessionLocked(Function<@Nullable MenuSession, HoloCloseReason> action) {
    HoloCloseReason reason = action.apply(session);
    if (reason == null) return null;
    Detached detached = detachSession(false);
    return detached == null ? null : new Closed(detached.handle(), reason);
  }

  private record Detached(ApiMenuHandle handle) {
  }

  private record Closed(ApiMenuHandle handle, HoloCloseReason reason) {
  }

  private record Opened(ApiMenuHandle replaced, ApiMenuHandle rejected, Throwable failure) {
  }

  record ClickSnapshot(String menuId, ApiMenuHandle handle, ClickableComponent<?> component, double distance) {
  }
}
