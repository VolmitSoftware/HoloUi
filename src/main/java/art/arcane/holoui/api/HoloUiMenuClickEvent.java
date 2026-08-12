package art.arcane.holoui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class HoloUiMenuClickEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();

  private final Player player;
  private final String menuId;
  private final String componentId;
  private final String ownerPluginName;
  private final HoloClickTrigger trigger;
  private boolean cancelled;

  public HoloUiMenuClickEvent(Player player, String menuId, String componentId, String ownerPluginName,
                              HoloClickTrigger trigger) {
    this.player = Objects.requireNonNull(player, "player");
    this.menuId = menuId;
    this.componentId = componentId;
    this.ownerPluginName = ownerPluginName;
    this.trigger = Objects.requireNonNull(trigger, "trigger");
  }

  public Player getPlayer() {
    return player;
  }

  public String getMenuId() {
    return menuId;
  }

  public String getComponentId() {
    return componentId;
  }

  public String getOwnerPluginName() {
    return ownerPluginName;
  }

  public HoloClickTrigger getTrigger() {
    return trigger;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    cancelled = cancel;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
