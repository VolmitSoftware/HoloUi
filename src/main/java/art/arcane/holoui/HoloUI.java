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

import com.github.retrooper.packetevents.PacketEvents;
import art.arcane.holoui.config.ConfigManager;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.holoui.service.HoloUiCommandService;
import art.arcane.holoui.util.common.SchedulerUtils;
import art.arcane.holoui.util.common.TextUtils;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.slimjar.app.builder.SpigotApplicationBuilder;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.util.logging.Level;

@Getter
public final class HoloUI extends JavaPlugin {
    public static HoloUI INSTANCE;

    private HoloUiCommandService commandService;
    private ConfigManager configManager;
    private MenuSessionManager sessionManager;

    private BuilderServer builderServer;
    private Metrics metrics;

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

    public HoloUI() {
        getLogger().info("Loading Dependencies...");
        new SpigotApplicationBuilder(this)
                .remap(true)
                .build();
        getLogger().info("Dependencies loaded!");
    }

    @Override
    public void onLoad() {
        INSTANCE = this;

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        ImageIO.scanForPlugins();
        PacketEvents.getAPI().init();
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

        if (builderServer != null) {
            builderServer.stopServer();
        }
        if (metrics != null) {
            metrics.shutdown();
        }
    }
}
