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
package art.arcane.holoui.service;

import art.arcane.holoui.HoloCommand;
import art.arcane.holoui.HoloUI;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.context.DirectorContextRegistry;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class HoloUiCommandService implements CommandExecutor, TabCompleter {
    private static final String ROOT_COMMAND = "holoui";
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final HoloUI plugin;
    private final HoloCommand commandRoot;
    private final DirectorTheme theme;
    private volatile DirectorRuntimeEngine director;

    public HoloUiCommandService(HoloUI plugin) {
        this.plugin = plugin;
        this.commandRoot = new HoloCommand(plugin);
        this.theme = DirectorThemes.forProduct(DirectorProduct.HOLOUI);
    }

    public void register() {
        PluginCommand command = plugin.getCommand(ROOT_COMMAND);
        if (command == null) {
            plugin.getLogger().warning("Failed to find command '" + ROOT_COMMAND + "'");
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
        getDirector();
    }

    private DirectorRuntimeEngine getDirector() {
        DirectorRuntimeEngine local = director;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            if (director != null) {
                return director;
            }

            director = DirectorEngineFactory.create(
                    commandRoot,
                    null,
                    buildDirectorContexts(),
                    null,
                    null,
                    null
            );

            return director;
        }
    }

    private DirectorContextRegistry buildDirectorContexts() {
        DirectorContextRegistry contexts = new DirectorContextRegistry();
        contexts.register(CommandSender.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender) {
                return sender.sender();
            }

            return null;
        });

        contexts.register(Player.class, (invocation, map) -> {
            if (invocation.getSender() instanceof BukkitDirectorSender sender && sender.sender() instanceof Player player) {
                return player;
            }

            return null;
        });

        return contexts;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!isRoot(command)) {
            return false;
        }

        if (!sender.hasPermission(HoloCommand.ROOT_PERM)) {
            sender.sendMessage(HoloCommand.PREFIX + "You lack the Permission '" + HoloCommand.ROOT_PERM + "'");
            return true;
        }

        String[] normalized = normalizeArgs(args);
        if (sendHelpIfRequested(sender, normalized)) {
            playSuccessSound(sender);
            return true;
        }

        DirectorExecutionResult result = runDirector(sender, label, normalized);
        if (result.isSuccess()) {
            playSuccessSound(sender);
            return true;
        }

        playFailureSound(sender);
        if (result.getMessage() == null || result.getMessage().trim().isEmpty()) {
            sender.sendMessage(HoloCommand.PREFIX + "Unknown command.");
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!isRoot(command) || !sender.hasPermission(HoloCommand.ROOT_PERM)) {
            return List.of();
        }

        return runDirectorTab(sender, alias, args);
    }

    private boolean isRoot(Command command) {
        return command.getName().equalsIgnoreCase(ROOT_COMMAND);
    }

    private String[] normalizeArgs(String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("builder")) {
            return new String[]{"builder", "status"};
        }

        return args;
    }

    private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
        Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(args), 9);
        if (page.isEmpty()) {
            return false;
        }

        DirectorMiniMenu.Theme helpTheme = DirectorMiniMenu.Theme.fromDirectorTheme(theme);
        for (String line : DirectorMiniMenu.render(page.get(), helpTheme)) {
            sendRich(sender, line);
        }

        return true;
    }

    private DirectorExecutionResult runDirector(CommandSender sender, String label, String[] args) {
        try {
            return getDirector().execute(new DirectorInvocation(new BukkitDirectorSender(sender), label, Arrays.asList(args)));
        } catch (Throwable e) {
            plugin.getLogger().warning("Director command execution failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return DirectorExecutionResult.notHandled();
        }
    }

    private List<String> runDirectorTab(CommandSender sender, String alias, String[] args) {
        try {
            return getDirector().tabComplete(new DirectorInvocation(new BukkitDirectorSender(sender), alias, Arrays.asList(args)));
        } catch (Throwable e) {
            plugin.getLogger().warning("Director tab completion failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            return List.of();
        }
    }

    private void playSuccessSound(CommandSender sender) {
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), theme.getSuccessSound(), SoundCategory.MASTER, 0.8f, 1.3f);
        }
    }

    private void playFailureSound(CommandSender sender) {
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), theme.getErrorSound(), SoundCategory.MASTER, 0.8f, 0.85f);
        }
    }

    private void sendRich(CommandSender sender, String miniMessage) {
        if (miniMessage == null || miniMessage.trim().isEmpty()) {
            return;
        }

        Component component = MINI_MESSAGE.deserialize(miniMessage);
        try {
            sender.getClass().getMethod("sendRichMessage", String.class).invoke(sender, miniMessage);
            return;
        } catch (Throwable ignored) {
        }

        try {
            sender.getClass().getMethod("sendMessage", Component.class).invoke(sender, component);
            return;
        } catch (Throwable ignored) {
        }

        sender.sendMessage(MINI_TAG_PATTERN.matcher(miniMessage).replaceAll(""));
    }

    private record BukkitDirectorSender(CommandSender sender) implements DirectorSender {
        @Override
        public String getName() {
            return sender.getName();
        }

        @Override
        public boolean isPlayer() {
            return sender instanceof Player;
        }

        @Override
        public void sendMessage(String message) {
            if (message != null && !message.trim().isEmpty()) {
                sender.sendMessage(message);
            }
        }
    }
}
