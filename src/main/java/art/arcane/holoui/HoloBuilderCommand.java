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

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.util.common.SchedulerUtils;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Decree(name = "builder", description = "HoloUI builder server controls")
public class HoloBuilderCommand {
    private static final String PREFIX = HoloCommand.PREFIX;

    @Decree(name = "status", description = "Show whether the HoloUI builder service is running")
    public void status(@Param(name = "sender", contextual = true, description = "Command sender context") CommandSender sender) {
        if (!sender.hasPermission(HoloCommand.ROOT_PERM + ".server")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
            return;
        }

        if (HoloUI.INSTANCE.getBuilderServer().isServerRunning()) {
            String host = HuiSettings.BUILDER_IP.value().equalsIgnoreCase("0.0.0.0") ? "localhost" : HuiSettings.BUILDER_IP.value();
            String url = host + ":" + HuiSettings.BUILDER_PORT.value();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Builder is running at " + ChatColor.WHITE + url + ChatColor.GREEN + ".");
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Builder is not running.");
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Use " + ChatColor.WHITE + "/holoui builder start" + ChatColor.GRAY + " or visit " + ChatColor.WHITE + "https://holoui.volmit.com/");
        }
    }

    @Decree(name = "start", description = "Start the HoloUI builder service")
    public void start(@Param(name = "sender", contextual = true, description = "Command sender context") CommandSender sender) {
        if (!sender.hasPermission(HoloCommand.ROOT_PERM + ".server.start")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
            return;
        }

        BuilderServer server = HoloUI.INSTANCE.getBuilderServer();
        if (server.isServerRunning()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Builder is already running.");
            return;
        }

        SchedulerUtils.runAsync(HoloUI.INSTANCE, () -> {
            sendOnSender(sender, PREFIX + ChatColor.GREEN + "Starting builder...");
            if (!server.prepareServer()) {
                sendOnSender(sender, PREFIX + ChatColor.RED + "An error occurred while setting up the builder. Check logs.");
                return;
            }

            server.startServer(HuiSettings.BUILDER_IP.value(), HuiSettings.BUILDER_PORT.value());
            runOnSender(sender, () -> status(sender));
        });
    }

    @Decree(name = "stop", description = "Stop the HoloUI builder service")
    public void stop(@Param(name = "sender", contextual = true, description = "Command sender context") CommandSender sender) {
        if (!sender.hasPermission(HoloCommand.ROOT_PERM + ".server.stop")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
            return;
        }

        if (HoloUI.INSTANCE.getBuilderServer().stopServer()) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Builder has been stopped.");
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Builder is not running.");
        }
    }

    private static void sendOnSender(CommandSender sender, String message) {
        runOnSender(sender, () -> sender.sendMessage(message));
    }

    private static void runOnSender(CommandSender sender, Runnable action) {
        if (sender instanceof Player player) {
            SchedulerUtils.runEntity(HoloUI.INSTANCE, player, action);
            return;
        }

        SchedulerUtils.runGlobal(HoloUI.INSTANCE, action);
    }
}
