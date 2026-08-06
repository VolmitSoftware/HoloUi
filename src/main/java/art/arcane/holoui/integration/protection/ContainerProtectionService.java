package art.arcane.holoui.integration.protection;

import art.arcane.holoui.api.HoloUiContainerPreviewAccessEvent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class ContainerProtectionService implements Listener {
  private static final String WORLD_GUARD = "WorldGuard";
  private static final ContainerProtectionProvider ALLOW_ALL = new ContainerProtectionProvider() {
    @Override
    public boolean canAccess(Player player, Block block) {
      return true;
    }

    @Override
    public boolean canAccess(Player player, Entity entity) {
      return true;
    }
  };
  private static final ContainerProtectionProvider DENY_ALL = new ContainerProtectionProvider() {
    @Override
    public boolean canAccess(Player player, Block block) {
      return false;
    }

    @Override
    public boolean canAccess(Player player, Entity entity) {
      return false;
    }
  };

  private final Plugin plugin;
  private final Consumer<Event> eventDispatcher;
  private final AtomicBoolean failureLogged = new AtomicBoolean();
  private volatile ContainerProtectionProvider provider;

  public ContainerProtectionService(Plugin plugin) {
    this(plugin, ALLOW_ALL, event -> Bukkit.getPluginManager().callEvent(event));
  }

  ContainerProtectionService(Plugin plugin, ContainerProtectionProvider provider, Consumer<Event> eventDispatcher) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.provider = Objects.requireNonNull(provider, "provider");
    this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
  }

  public void activate() {
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Plugin worldGuard = Bukkit.getPluginManager().getPlugin(WORLD_GUARD);
    if (worldGuard != null && worldGuard.isEnabled()) {
      installWorldGuard(worldGuard);
    }
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    provider = ALLOW_ALL;
  }

  public boolean canAccess(Player player, Block block) {
    try {
      if (!provider.canAccess(player, block)) {
        return false;
      }
      HoloUiContainerPreviewAccessEvent event = new HoloUiContainerPreviewAccessEvent(player, block);
      eventDispatcher.accept(event);
      return !event.isCancelled();
    } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
      logFailure(ex);
      return false;
    }
  }

  public boolean canAccess(Player player, Entity entity) {
    try {
      if (!provider.canAccess(player, entity)) {
        return false;
      }
      HoloUiContainerPreviewAccessEvent event = new HoloUiContainerPreviewAccessEvent(player, entity);
      eventDispatcher.accept(event);
      return !event.isCancelled();
    } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
      logFailure(ex);
      return false;
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPluginEnable(PluginEnableEvent event) {
    if (event.getPlugin().getName().equals(WORLD_GUARD)) {
      installWorldGuard(event.getPlugin());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPluginDisable(PluginDisableEvent event) {
    if (event.getPlugin().getName().equals(WORLD_GUARD)) {
      provider = ALLOW_ALL;
      failureLogged.set(false);
    }
  }

  private void installWorldGuard(Plugin worldGuard) {
    try {
      provider = new WorldGuardContainerProtectionProvider(worldGuard);
      failureLogged.set(false);
      plugin.getLogger().info("Container previews are using WorldGuard access checks.");
    } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
      provider = DENY_ALL;
      logFailure(ex);
    }
  }

  private void logFailure(Throwable throwable) {
    if (failureLogged.compareAndSet(false, true)) {
      plugin.getLogger().log(Level.SEVERE,
          "Container access protection failed. Previews will remain locked until the protection provider recovers.", throwable);
    }
  }
}
