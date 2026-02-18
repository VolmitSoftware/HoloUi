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

import art.arcane.holoui.config.ConfigManager;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.holoui.service.HoloUiCommandService;
import art.arcane.holoui.util.common.TextUtils;
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
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.util.Collection;
import java.util.logging.Level;

@Getter
public final class HoloUI extends JavaPlugin {
  public static HoloUI INSTANCE;

  private HoloUiCommandService commandService;
  private ConfigManager configManager;
  private MenuSessionManager sessionManager;

  private BuilderServer builderServer;
  private Metrics metrics;

  public HoloUI() {
    getLogger().info("Loading Dependencies...");
    new SpigotApplicationBuilder(this)
        .remap(true)
        .build();
    getLogger().info("Dependencies loaded!");
  }

  public static void log(Level logLevel, String s, Object... args) {
    INSTANCE.getLogger().log(logLevel, args.length > 0 ? String.format(s, args) : s);
  }

  public static void logException(boolean isSevere, Throwable e, int indents) {
    StringBuilder format = new StringBuilder("%s%s");
    for (int i = 0; i < indents; i++)
      format.insert(0, "\t");
    log(isSevere ? Level.SEVERE : Level.WARNING,
        format.toString(), e.getClass().getSimpleName(), e.getMessage() != null ? " - " + e.getMessage() : "");
  }

  public static void logExceptionStack(boolean isSevere, Throwable e, String s, Object... args) {
    log(isSevere ? Level.SEVERE : Level.WARNING, s, args);
    int indent = 1;
    Throwable throwable = e;
    while (throwable != null) {
      logException(isSevere, throwable, indent++);
      throwable = throwable.getCause();
    }
  }

  @Override
  public void onLoad() {
    INSTANCE = this;

    SpigotPacketEventsBuilder.clearBuildCache();
    PacketEventsSettings packetEventsSettings = new PacketEventsSettings()
        .checkForUpdates(true);
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

    this.configManager = new ConfigManager(getDataFolder());
    this.sessionManager = new MenuSessionManager();
    this.commandService = new HoloUiCommandService(this);
    commandService.register();

    this.builderServer = new BuilderServer(getDataFolder());
    this.metrics = new Metrics(this, 24222);
  }

  @Override
  public void onDisable() {
    SchedulerUtils.cancelPluginTasks(this);

    if (configManager != null) {
      configManager.shutdown();
    }
    if (sessionManager != null) {
      sessionManager.destroyAll();
    }
    if (PacketEvents.getAPI() != null) {
      PacketEvents.getAPI().terminate();
    }
    SpigotPacketEventsBuilder.clearBuildCache();

    if (builderServer != null) {
      builderServer.stopServer();
    }
    if (metrics != null) {
      metrics.shutdown();
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
