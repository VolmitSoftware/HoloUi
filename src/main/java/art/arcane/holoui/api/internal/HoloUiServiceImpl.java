package art.arcane.holoui.api.internal;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloComponent;
import art.arcane.holoui.api.HoloMenu;
import art.arcane.holoui.api.HoloMenuHandle;
import art.arcane.holoui.api.HoloUiService;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.volmlib.util.bukkit.Events;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HoloUiServiceImpl implements HoloUiService {
  private final Plugin plugin;
  private final Logger logger;
  private final ApiBackend backend;
  private final Set<ApiMenuHandle> handles = ConcurrentHashMap.newKeySet();

  private volatile boolean active = true;
  private volatile Events pluginDisableListener;

  public HoloUiServiceImpl(HoloUI plugin) {
    this(Objects.requireNonNull(plugin, "plugin"), plugin.getLogger(), new HoloUiBackend(plugin));
  }

  HoloUiServiceImpl(Plugin plugin, Logger logger, ApiBackend backend) {
    this.plugin = plugin;
    this.logger = Objects.requireNonNull(logger, "logger");
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public void register() {
    active = true;
    Bukkit.getServicesManager().register(HoloUiService.class, this, plugin, ServicePriority.Normal);
    pluginDisableListener = Events.listen(plugin, PluginDisableEvent.class, event -> onOwnerDisabled(event.getPlugin()));
    HoloUI.log(Level.INFO, "HoloUi menu API registered on the ServicesManager");
  }

  public void unregister() {
    active = false;
    Bukkit.getServicesManager().unregister(HoloUiService.class, this);

    Events listener = pluginDisableListener;
    if (listener != null) {
      listener.unregister();
      pluginDisableListener = null;
    }

    for (ApiMenuHandle handle : handles) {
      handle.terminate(HoloCloseReason.HOLOUI_SHUTDOWN);
    }

    handles.clear();
  }

  public int openHandleCount() {
    return handles.size();
  }

  @Override
  public HoloMenuHandle open(Plugin owner, Player player, HoloMenu menu) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(menu, "menu");

    ApiOwner apiOwner = ApiOwner.of(owner);
    MenuDefinitionData definition = ApiMenuTranslator.definition(menu);
    Map<String, HoloClickHandler> handlers = ApiMenuTranslator.handlers(menu);
    ApiMenuHandle handle = newHandle(player, menu.id(), apiOwner, handlers, componentIds(menu));

    if (!accept(handle, apiOwner)) {
      return handle;
    }

    dispatchOpen(player, handle, apiOwner, () -> definition);
    return handle;
  }

  @Override
  public HoloMenuHandle open(Plugin owner, Player player, String menuId) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(menuId, "menuId");

    ApiOwner apiOwner = ApiOwner.of(owner);
    ApiMenuHandle handle = newHandle(player, menuId, apiOwner, Map.of(), Set.of());

    if (!accept(handle, apiOwner)) {
      return handle;
    }

    dispatchOpen(player, handle, apiOwner, () -> resolvePermitted(player, menuId));
    return handle;
  }

  @Override
  public boolean close(Player player) {
    if (!active || player == null) {
      return false;
    }

    if (!backend.hasSession(player)) {
      return false;
    }

    return backend.schedule(player, () -> backend.closeSession(player, HoloCloseReason.CLOSED_BY_OWNER), null);
  }

  @Override
  public boolean isOpen(Player player) {
    return active && player != null && backend.hasSession(player);
  }

  @Override
  public Set<String> menuIds() {
    return active ? backend.menuIds() : Set.of();
  }

  private ApiMenuHandle newHandle(Player player, String menuId, ApiOwner owner,
                                  Map<String, HoloClickHandler> handlers, Set<String> componentIds) {
    return new ApiMenuHandle(player.getUniqueId(), menuId, owner, handlers, componentIds, logger,
        this::requestClose, handles::remove);
  }

  private boolean accept(ApiMenuHandle handle, ApiOwner owner) {
    if (!active) {
      handle.terminate(HoloCloseReason.OPEN_FAILED);
      return false;
    }

    if (!owner.active()) {
      handle.terminate(HoloCloseReason.OWNER_DISABLED);
      return false;
    }

    handles.add(handle);
    return true;
  }

  private void dispatchOpen(Player player, ApiMenuHandle handle, ApiOwner owner,
                            Supplier<MenuDefinitionData> definition) {
    Runnable task = () -> openNow(player, handle, owner, definition);
    Runnable retired = () -> handle.terminate(HoloCloseReason.QUIT);

    if (!backend.schedule(player, task, retired)) {
      handle.terminate(HoloCloseReason.OPEN_FAILED);
    }
  }

  private void openNow(Player player, ApiMenuHandle handle, ApiOwner owner,
                       Supplier<MenuDefinitionData> supplier) {
    if (!handle.live()) {
      return;
    }

    if (!active) {
      handle.terminate(HoloCloseReason.HOLOUI_SHUTDOWN);
      return;
    }

    if (!player.isOnline()) {
      handle.terminate(HoloCloseReason.QUIT);
      return;
    }

    try {
      if (!owner.active()) {
        handle.terminate(HoloCloseReason.OWNER_DISABLED);
        return;
      }

      MenuDefinitionData definition = supplier.get();

      if (definition == null) {
        handle.terminate(HoloCloseReason.DENIED);
        return;
      }

      if (!backend.openSession(player, definition, handle)) {
        handle.terminate(HoloCloseReason.DENIED);
      }
    } catch (Throwable error) {
      logger.log(Level.SEVERE, "Failed to open API menu \"" + handle.menuId() + "\" for " + player.getName()
          + " on behalf of " + owner.name(), error);
      handle.terminate(HoloCloseReason.OPEN_FAILED);
    }
  }

  private MenuDefinitionData resolvePermitted(Player player, String menuId) {
    MenuDefinitionData definition = backend.definition(menuId);

    if (definition == null) {
      return null;
    }

    return player.hasPermission("holoui.open." + definition.getId()) ? definition : null;
  }

  private void requestClose(ApiMenuHandle handle, HoloCloseReason reason) {
    if (!handle.live()) {
      return;
    }

    Player player = backend.online(handle.playerId());

    if (player == null) {
      handle.terminate(reason);
      return;
    }

    Runnable task = () -> {
      if (!backend.closeSessionOf(player, handle, reason)) {
        handle.terminate(reason);
      }
    };

    if (!backend.schedule(player, task, () -> handle.terminate(reason))) {
      handle.terminate(reason);
    }
  }

  private void onOwnerDisabled(Plugin disabled) {
    if (disabled == null || disabled == plugin) {
      return;
    }

    String name = disabled.getName();
    backend.forgetClickGuard(name);

    for (ApiMenuHandle handle : handles) {
      if (handle.owner().name().equals(name)) {
        requestClose(handle, HoloCloseReason.OWNER_DISABLED);
      }
    }
  }

  private static Set<String> componentIds(HoloMenu menu) {
    Set<String> ids = new HashSet<>(menu.components().size());

    for (HoloComponent component : menu.components()) {
      ids.add(component.id());
    }

    return ids;
  }
}
