package art.arcane.holoui.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class HoloUiMenuOpenEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();

  private final Player player;
  private final String menuId;
  private final String ownerPluginName;
  private boolean cancelled;

  public HoloUiMenuOpenEvent(Player player, String menuId, String ownerPluginName) {
    this.player = Objects.requireNonNull(player, "player");
    this.menuId = menuId;
    this.ownerPluginName = ownerPluginName;
  }

  public Player getPlayer() {
    return player;
  }

  public String getMenuId() {
    return menuId;
  }

  public String getOwnerPluginName() {
    return ownerPluginName;
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
