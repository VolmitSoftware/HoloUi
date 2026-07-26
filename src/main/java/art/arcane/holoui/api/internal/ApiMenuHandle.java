package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenuHandle;
import art.arcane.holoui.api.HoloMenuState;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class ApiMenuHandle implements HoloMenuHandle {
  private final UUID sessionId = UUID.randomUUID();
  private final UUID playerId;
  private final String menuId;
  private final ApiOwner owner;
  private final Map<String, HoloClickHandler> handlers;
  private final Set<String> componentIds;
  private final ApiHandleState state;
  private final ApiPendingIcons pending = new ApiPendingIcons();
  private final BiConsumer<ApiMenuHandle, HoloCloseReason> closeRequest;
  private final Consumer<ApiMenuHandle> onTerminated;

  public ApiMenuHandle(UUID playerId, String menuId, ApiOwner owner, Map<String, HoloClickHandler> handlers,
                       Set<String> componentIds, Logger logger,
                       BiConsumer<ApiMenuHandle, HoloCloseReason> closeRequest,
                       Consumer<ApiMenuHandle> onTerminated) {
    this.playerId = Objects.requireNonNull(playerId, "playerId");
    this.menuId = Objects.requireNonNull(menuId, "menuId");
    this.owner = Objects.requireNonNull(owner, "owner");
    this.handlers = Map.copyOf(handlers);
    this.componentIds = Set.copyOf(componentIds);
    this.state = new ApiHandleState(logger, owner.name());
    this.closeRequest = Objects.requireNonNull(closeRequest, "closeRequest");
    this.onTerminated = Objects.requireNonNull(onTerminated, "onTerminated");
  }

  @Override
  public UUID sessionId() {
    return sessionId;
  }

  @Override
  public UUID playerId() {
    return playerId;
  }

  @Override
  public String menuId() {
    return menuId;
  }

  @Override
  public HoloMenuState state() {
    return state.state();
  }

  @Override
  public boolean setText(String componentId, String miniMessage) {
    return stage(componentId, HoloIcon.text(miniMessage));
  }

  @Override
  public boolean setItem(String componentId, ItemStack stack) {
    if (stack == null) {
      return false;
    }

    return stage(componentId, HoloIcon.item(stack));
  }

  @Override
  public boolean setIcon(String componentId, HoloIcon icon) {
    if (icon == null) {
      return false;
    }

    return stage(componentId, icon);
  }

  @Override
  public void close() {
    if (!state.live()) {
      return;
    }

    closeRequest.accept(this, HoloCloseReason.CLOSED_BY_OWNER);
  }

  @Override
  public HoloMenuHandle onClosed(Consumer<HoloCloseReason> callback) {
    state.onClosed(callback);
    return this;
  }

  public ApiOwner owner() {
    return owner;
  }

  public HoloClickHandler handler(String componentId) {
    return handlers.get(componentId);
  }

  public boolean live() {
    return state.live();
  }

  public boolean markOpen() {
    return state.markOpen();
  }

  public boolean terminate(HoloCloseReason reason) {
    boolean terminated = state.terminate(reason, reason == HoloCloseReason.DENIED
        || reason == HoloCloseReason.OPEN_FAILED);

    if (terminated) {
      pending.discard();
      onTerminated.accept(this);
    }

    return terminated;
  }

  public boolean dirty() {
    return pending.dirty();
  }

  public int drain(BiConsumer<String, HoloIcon> sink) {
    return pending.drain(sink);
  }

  private boolean stage(String componentId, HoloIcon icon) {
    if (componentId == null || !componentIds.contains(componentId) || !state.live()) {
      return false;
    }

    pending.stage(componentId, icon);
    return true;
  }
}
