package art.arcane.holoui.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class HoloUiContainerPreviewAccessEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();

  private final Player player;
  private final Block block;
  private final Entity entity;
  private boolean cancelled;

  public HoloUiContainerPreviewAccessEvent(Player player, Block block) {
    this.player = Objects.requireNonNull(player, "player");
    this.block = Objects.requireNonNull(block, "block");
    this.entity = null;
  }

  public HoloUiContainerPreviewAccessEvent(Player player, Entity entity) {
    this.player = Objects.requireNonNull(player, "player");
    this.block = null;
    this.entity = Objects.requireNonNull(entity, "entity");
  }

  public Player getPlayer() {
    return player;
  }

  public Block getBlock() {
    return block;
  }

  public Entity getEntity() {
    return entity;
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
