package art.arcane.holoui.menu;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.components.ClickableComponent;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.special.inventories.ContainerPreview;
import art.arcane.holoui.service.HoloUiTelemetry;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import lombok.Synchronized;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

class SessionHolder {
  private final Object sessionLock = new Object();
  private final Object previewLock = new Object();

  private final Player player;
  private final UUID playerId;
  private final PlayerSnapshotStore<String> openMenus;
  private transient volatile MenuSession session;
  private transient ContainerPreview preview;
  private transient volatile String lastSession;

  SessionHolder(Player player, PlayerSnapshotStore<String> openMenus) {
    this.player = player;
    this.playerId = player.getUniqueId();
    this.openMenus = openMenus;
  }

  void openSession(MenuDefinitionData data, ApiMenuHandle handle) {
    Opened opened = openSessionLocked(data, handle);
    if (opened.replaced() != null) {
      opened.replaced().terminate(HoloCloseReason.REPLACED);
    }
    if (opened.rejected() != null) {
      opened.rejected().terminate(HoloCloseReason.QUIT);
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
        session.drainApiUpdates();
        session.getComponents().forEach(MenuComponent::tick);
      }
    }

    synchronized (previewLock) {
      if (preview != null) {
        try {
          preview.tick();
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

  ClickSnapshot snapshotClick() {
    MenuSession current = session;
    if (current == null) return null;

    List<ClickableComponent<?>> hits = null;
    for (MenuComponent<?> component : current.getComponents()) {
      if (component instanceof ClickableComponent<?> clickable && clickable.isOpen() && clickable.isSelected()) {
        if (hits == null) hits = new ArrayList<>(2);
        hits.add(clickable);
      }
    }

    if (hits == null) return null;
    return new ClickSnapshot(current.getId(), current.getApiHandle(), hits);
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
    return lastSession;
  }

  void refreshVisuals() {
    synchronized (sessionLock) {
      if (session != null) {
        session.getComponents().forEach(component -> {
          if (!component.isOpen()) return;
          component.close();
          component.open(true);
        });
      }
    }
    synchronized (previewLock) {
      if (preview != null) {
        preview.refreshVisuals();
      }
    }
  }

  @Synchronized("sessionLock")
  private Opened openSessionLocked(MenuDefinitionData data, ApiMenuHandle handle) {
    if (!player.isOnline()) return new Opened(null, handle);

    Detached previous = detachSession(true);
    session = new MenuSession(data, player, handle);
    HoloUiTelemetry.incrementMenusOpen();
    openMenus.publish(playerId, session.getId());
    session.open();
    if (handle != null) {
      handle.markOpen();
    }

    return new Opened(previous == null ? null : previous.handle(), null);
  }

  @Synchronized("sessionLock")
  private Detached detachSession(boolean history) {
    if (session == null) return null;
    lastSession = history ? session.getId() : null;
    ApiMenuHandle handle = session.getApiHandle();
    session.close();
    session = null;
    openMenus.publish(playerId, null);
    HoloUiTelemetry.decrementMenusOpen();
    return new Detached(handle);
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

  private record Opened(ApiMenuHandle replaced, ApiMenuHandle rejected) {
  }

  record ClickSnapshot(String menuId, ApiMenuHandle handle, List<ClickableComponent<?>> components) {
  }
}
