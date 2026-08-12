/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui;

import art.arcane.holoui.api.internal.HoloUiServiceImpl;
import art.arcane.holoui.board.BoardRuntimeManager;
import art.arcane.holoui.board.BoardService;
import art.arcane.holoui.config.ConfigManager;
import art.arcane.holoui.integration.ItemProviderRegistry;
import art.arcane.holoui.editor.sync.EditorSyncService;
import art.arcane.holoui.integration.protection.ContainerProtectionService;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.holoui.menu.special.inventories.PreviewScaleService;
import art.arcane.holoui.menu.special.inventories.doc.PreviewDocumentRegistry;
import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import art.arcane.holoui.persistence.HoloUiProjectTransaction;
import art.arcane.holoui.service.HoloUiCommandService;
import art.arcane.holoui.service.HoloUiIntegrationService;
import art.arcane.holoui.service.HoloUiPlaceholderInstaller;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.volmlib.integration.ReloadAware;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.hud.HudBossBarLane;
import art.arcane.volmlib.util.hud.HudSlotService;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public final class HoloUI extends JavaPlugin implements ReloadAware {
  public static HoloUI INSTANCE;

  private HoloUiCommandService commandService;
  private HoloLocalization localization;
  private ConfigManager configManager;
  private BoardService boardService;
  private BoardRuntimeManager boardRuntime;
  private ItemProviderRegistry itemProviders;
  private PreviewDocumentRegistry previewRegistry;
  private ContainerProtectionService containerProtection;
  private MenuSessionManager sessionManager;
  private HudSlotService hudSlots;
  private HudBossBarLane hudLanes;
  private HoloUiPersistenceCoordinator persistenceCoordinator;
  private HoloUiProjectTransaction projectTransaction;
  private EditorSyncService editorSyncService;

  private HoloUiIntegrationService integrationService;
  private HoloUiServiceImpl apiService;
  private PlaceholderRegistration placeholderRegistration;
  // bstats.org plugin id
  private static final int BSTATS_PLUGIN_ID = 24222;
  // HoloUiMetrics owns all bstats types; never reference them from this class (slimjar link trap)
  private HoloUiMetrics metrics;
  private final AtomicBoolean alreadyDrained = new AtomicBoolean(false);

  public HoloUI() {
    getLogger().info("Loading Dependencies...");
    new SpigotApplicationBuilder(this)
        .build();
    getLogger().info("Dependencies loaded!");
  }

  public static void log(Level logLevel, String s, Object... args) {
    logger().log(logLevel, args.length > 0 ? String.format(s, args) : s);
  }

  private static Logger logger() {
    HoloUI instance = INSTANCE;
    return instance == null ? Logger.getLogger("HoloUi") : instance.getLogger();
  }

  public static void logException(boolean isSevere, Throwable e, int indents) {
    StringBuilder format = new StringBuilder("%s%s");
    for (int i = 0; i < indents; i++)
      format.insert(0, "\t");
    log(isSevere ? Level.SEVERE : Level.WARNING,
        format.toString(), e.getClass().getSimpleName(), e.getMessage() != null ? " - " + e.getMessage() : "");
  }

  public static void logExceptionStack(boolean isSevere, Throwable e, String s, Object... args) {
    String message = args.length > 0 ? String.format(s, args) : s;
    logger().log(isSevere ? Level.SEVERE : Level.WARNING, message, e);
  }

  @Override
  public void onLoad() {
    INSTANCE = this;

    SpigotPacketEventsBuilder.clearBuildCache();
    PacketEventsSettings packetEventsSettings = new PacketEventsSettings()
        .checkForUpdates(false);
    PacketEvents.setAPI(SpigotPacketEventsBuilder.buildNoCache(this, packetEventsSettings));
    PacketEvents.getAPI().load();
  }

  @Override
  public void onEnable() {
    ImageIO.scanForPlugins();
    prewarmPacketEventsUsers();
    try {
      PacketEvents.getAPI().init();
    } catch (NullPointerException ex) {
      if (!isPacketEventsUserBindFailure(ex)) {
        throw ex;
      }
      prewarmPacketEventsUsers();
      PacketEvents.getAPI().init();
    }
    TextUtils.splash(this);
    getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

    this.hudSlots = new HudSlotService(this);
    this.hudLanes = new HudBossBarLane();
    this.localization = new HoloLocalization(getDataFolder(), getLogger());
    this.persistenceCoordinator = new HoloUiPersistenceCoordinator();
    this.projectTransaction = new HoloUiProjectTransaction(getDataFolder().toPath());
    try {
      persistenceCoordinator.write(() -> {
        projectTransaction.recover();
        return null;
      });
    } catch (Exception failure) {
      throw new IllegalStateException("Unable to recover HoloUI editor sync persistence", failure);
    }
    this.configManager = new ConfigManager(getDataFolder());
    this.boardService = new BoardService(this);
    boardService.start();
    // Documents compile against the localization catalog and must be fully published before
    // MenuSessionManager's raycast tick can ask the registry what a player is looking at.
    this.previewRegistry = new PreviewDocumentRegistry(getDataFolder());
    previewRegistry.startWatching();
    this.itemProviders = new ItemProviderRegistry(this);
    itemProviders.activateAll();
    this.containerProtection = new ContainerProtectionService(this);
    containerProtection.activate();
    this.sessionManager = new MenuSessionManager();
    this.boardRuntime = new BoardRuntimeManager(this, boardService);
    this.editorSyncService = new EditorSyncService(this);
    try {
      editorSyncService.start();
    } catch (RuntimeException failure) {
      getLogger().log(Level.SEVERE,
          "Editor sync was disabled because its secure session store could not be loaded. "
              + "Repair or remove editor-sync-sessions.json; HoloUi will keep one-way editor handoffs available.",
          failure);
    }
    PreviewScaleService.init(this);
    this.commandService = new HoloUiCommandService(this);
    commandService.register();

    if (BSTATS_PLUGIN_ID > 0) {
      this.metrics = HoloUiMetrics.start(this, BSTATS_PLUGIN_ID);
    }

    this.integrationService = new HoloUiIntegrationService();
    integrationService.register();

    this.apiService = new HoloUiServiceImpl(this);
    apiService.register();

    this.placeholderRegistration = new PlaceholderRegistration(getLogger());
    if (PlaceholderRegistration.isPlaceholderApiEnabled()) {
      HoloUiPlaceholderInstaller.install(placeholderRegistration, sessionManager.getOpenMenus(), getLogger());
    }
  }

  @Override
  public void onDisable() {
    drain();
  }

  @Override
  public void onPreUnload(ReloadAware.PreUnloadReason reason) {
    log(Level.INFO, "BileTools pre-unload hook fired (%s). Tearing down HoloUI sessions and PacketEvents.", reason);
    drain();
  }

  private void drain() {
    if (!alreadyDrained.compareAndSet(false, true)) {
      return;
    }
    if (placeholderRegistration != null) {
      placeholderRegistration.unregister();
    }
    if (apiService != null) {
      apiService.unregister();
    }
    if (integrationService != null) {
      integrationService.unregister();
    }
    if (containerProtection != null) {
      containerProtection.shutdown();
    }
    if (commandService != null) {
      commandService.shutdown();
    }
    if (editorSyncService != null) {
      editorSyncService.shutdown();
    }
    if (configManager != null) {
      configManager.shutdown();
    }
    if (boardRuntime != null) {
      boardRuntime.shutdown();
    }
    if (boardService != null) {
      boardService.shutdown();
    }
    if (sessionManager != null) {
      sessionManager.destroyAll();
    }
    if (itemProviders != null) {
      itemProviders.shutdown();
    }
    PreviewScaleService.shutdown();
    if (hudLanes != null) {
      hudLanes.shutdown();
    }
    if (hudSlots != null) {
      hudSlots.shutdown();
    }
    if (PacketEvents.getAPI() != null) {
      PacketEvents.getAPI().terminate();
    }
    SpigotPacketEventsBuilder.clearBuildCache();

    if (metrics != null) {
      metrics.shutdown();
    }

    getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
    SchedulerUtils.cancelPluginTasks(this);
    if (INSTANCE == this) {
      INSTANCE = null;
    }
  }

  private void prewarmPacketEventsUsers() {
    PacketEventsAPI<?> api = PacketEvents.getAPI();
    if (api == null) {
      return;
    }

    PlayerManager playerManager = api.getPlayerManager();
    ProtocolManager protocolManager = api.getProtocolManager();
    ClientVersion fallbackVersion = api.getServerManager().getVersion().toClientVersion();
    Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
    for (Player player : onlinePlayers) {
      Object channel = playerManager.getChannel(player);
      if (channel == null) {
        continue;
      }

      User existingUser = protocolManager.getUser(channel);
      if (existingUser != null) {
        continue;
      }

      UserProfile profile = new UserProfile(player.getUniqueId(), player.getName());
      User newUser = new User(channel, ConnectionState.PLAY, fallbackVersion, profile);
      protocolManager.setUser(channel, newUser);
    }
  }

  private boolean isPacketEventsUserBindFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      for (StackTraceElement element : current.getStackTrace()) {
        if (element.getClassName().endsWith("SpigotChannelInjector") && element.getMethodName().equals("updatePlayer")) {
          return true;
        }
      }
      current = current.getCause();
    }
    return false;
  }
}
