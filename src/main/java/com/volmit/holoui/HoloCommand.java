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
package com.volmit.holoui;

import co.aikar.commands.*;
import co.aikar.commands.annotation.*;
import com.volmit.holoui.config.HuiSettings;
import com.volmit.holoui.config.MenuDefinitionData;
import com.volmit.holoui.utils.SchedulerUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static com.volmit.holoui.HoloUI.INSTANCE;

@CommandAlias("holoui|holo|hui|holou|hu")
@CommandPermission(HoloCommand.ROOT_PERM)
public class HoloCommand extends BaseCommand {

    public static final String PREFIX = "[HoloUI]: ";
    public static final String ROOT_PERM = "holoui.command";

    protected void registerCompletions(CommandCompletions<BukkitCommandCompletionContext> completions) {
        completions.registerAsyncCompletion("menu", context -> {
            var sender = context.getSender();
            return INSTANCE.getConfigManager().keys()
                    .stream()
                    .filter(s -> sender.hasPermission("holoui.open." + s))
                    .toList();
        });
    }

    protected void registerContexts(CommandContexts<BukkitCommandExecutionContext> contexts) {
        contexts.registerContext(MenuDefinitionData.class, context -> {
            String ui = String.join(" ", context.getArgs());
            return INSTANCE.getConfigManager()
                    .get(ui)
                    .orElseThrow(() -> new IllegalArgumentException(PREFIX + ChatColor.RED + "\"" + ui + "\" is not available."));
        });
    }

    @Subcommand("list")
    @Description("List all menus")
    @CommandPermission(ROOT_PERM + ".list")
    public void list(CommandSender sender) {
        if (INSTANCE.getConfigManager().keys().isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "No menus are available.");
            return;
        }

        sender.sendMessage(ChatColor.GRAY + "----------+=== Menus ===+----------");
        INSTANCE.getConfigManager().keys().forEach(s -> {
            var component = new TextComponent(ChatColor.GRAY + "  - " + ChatColor.WHITE + s);
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/holo open " + s));
            sender.spigot().sendMessage(component);
        });
        sender.sendMessage(ChatColor.GRAY + "----------------------------------");
    }

    @Default
    @Subcommand("open")
    @Description("Open a menu")
    public void open(CommandSender sender) {
        list(sender);
    }

    @Subcommand("open")
    @Description("Open a menu")
    @CommandCompletion("@menu")
    @CommandPermission(ROOT_PERM + ".open")
    public void open(Player player, MenuDefinitionData ui) {
        if (!player.hasPermission("holoui.open." + ui.getId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "You lack permission to open \"" + ui + "\".");
            return;
        }

        try {
            INSTANCE.getSessionManager().createNewSession(player, ui);
        } catch (NullPointerException e) {
            HoloUI.logExceptionStack(true, e, "Null in session creation?");
        }
    }

    @Subcommand("close")
    @Description("Close the current menu")
    @CommandPermission(ROOT_PERM + ".close")
    public void close(Player player) {
        if (INSTANCE.getSessionManager().destroySession(player))
            player.sendMessage(PREFIX + ChatColor.GREEN + "Menu closed.");
        else
            player.sendMessage(PREFIX + ChatColor.RED + "No menu is currently open.");
    }

    @Subcommand("builder")
    @Description("Builder server status")
    @CommandPermission(ROOT_PERM + ".server")
    public void serverStatus(CommandSender sender) {
        if (INSTANCE.getBuilderServer().isServerRunning()) {
            String host = HuiSettings.BUILDER_IP.value().equalsIgnoreCase("0.0.0.0") ? "localhost" : HuiSettings.BUILDER_IP.value();
            String url = host + ":" + HuiSettings.BUILDER_PORT.value();
            sender.spigot().sendMessage(new ComponentBuilder(PREFIX)
                    .append(new ComponentBuilder("Builder is running at ")
                            .color(net.md_5.bungee.api.ChatColor.GREEN)
                            .create())
                    .append(new ComponentBuilder(url)
                            .underlined(true)
                            .color(net.md_5.bungee.api.ChatColor.WHITE)
                            .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "http://" + url))
                            .create())
                    .append(new ComponentBuilder(".")
                            .color(net.md_5.bungee.api.ChatColor.GREEN)
                            .underlined(false)
                            .create())
                    .create());
        } else {
            sender.spigot().sendMessage(new ComponentBuilder(PREFIX)
                    .append(new ComponentBuilder("Builder is not running. Start it ")
                            .color(net.md_5.bungee.api.ChatColor.RED)
                            .create())
                    .append(new ComponentBuilder("here")
                            .underlined(true)
                            .color(net.md_5.bungee.api.ChatColor.WHITE)
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/holoui builder start"))
                            .create())
                    .append(new ComponentBuilder(", or visit ")
                            .color(net.md_5.bungee.api.ChatColor.RED)
                            .underlined(false)
                            .create())
                    .append(new ComponentBuilder("here")
                            .underlined(true)
                            .color(net.md_5.bungee.api.ChatColor.WHITE)
                            .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://holoui.volmit.com/"))
                            .create())
                    .append(new ComponentBuilder(" for a online version.")
                            .color(net.md_5.bungee.api.ChatColor.RED)
                            .underlined(false)
                            .create())
                    .create());
        }
    }

    @Subcommand("builder start")
    @Description("Start the builder server")
    @CommandPermission(ROOT_PERM + ".server.start")
    public void startServer(CommandSender sender) {
        BuilderServer server = INSTANCE.getBuilderServer();
        if (server.isServerRunning()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Builder is already running.");
            return;
        }
        SchedulerUtils.runAsync(INSTANCE, () -> {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Starting builder...");
            if (!server.prepareServer())
                sender.sendMessage(PREFIX + ChatColor.RED + "An error occurred while setting up the builder! Check the logs for details.");
            server.startServer(HuiSettings.BUILDER_IP.value(), HuiSettings.BUILDER_PORT.value());
            serverStatus(sender);
        });
    }

    @Subcommand("builder stop")
    @Description("Stopps the builder server")
    @CommandPermission(ROOT_PERM + ".server.stop")
    public void stopServer(CommandSender sender) {
        if (INSTANCE.getBuilderServer().stopServer())
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Builder has been stopped.");
        else
            sender.sendMessage(PREFIX + ChatColor.RED + "Builder is not running.");
    }
}
