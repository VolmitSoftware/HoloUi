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
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.DirectorTextResolver;
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
import art.arcane.volmlib.util.localization.MessageArgs;
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
import java.util.Locale;
import java.util.Optional;

public final class HoloUiCommandService implements CommandExecutor, TabCompleter {
  private static final String ROOT_COMMAND = "holoui";

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

  public void shutdown() {
    commandRoot.shutdown();
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
          DirectorEngineOptions.builder()
              .contexts(buildDirectorContexts())
              .textResolver(plugin.getLocalization().directorResolver())
              .build()
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
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.PERMISSION_DENIED,
          MessageArgs.builder().untrusted("permission", HoloCommand.ROOT_PERM).build()
      ));
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
      sender.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.UNKNOWN_COMMAND,
          MessageArgs.builder().untrusted("command", String.join(" ", normalized)).build()
      ));
    }

    return true;
  }

  @Nullable
  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
    if (!isRoot(command) || !sender.hasPermission(HoloCommand.ROOT_PERM)) {
      return List.of();
    }

    String[] normalized = normalizeTabArgs(args);
    return restorePositionalSuggestions(args, runDirectorTab(sender, alias, normalized));
  }

  private boolean isRoot(Command command) {
    return command.getName().equalsIgnoreCase(ROOT_COMMAND);
  }

  static String[] normalizeArgs(String[] args) {
    args = normalizeTrailingText(args);

    if (args.length == 1 && isGroup(args[0], "preview", "previews")) {
      return new String[]{"preview", "list"};
    }

    if (args.length == 1 && isGroup(args[0], "board", "boards")) {
      return new String[]{"board", "list"};
    }

    if (args.length == 1 && isGroup(args[0], "menu", "menus")) {
      return new String[]{"list"};
    }

    if (args.length == 2
        && args[0].equalsIgnoreCase("open")
        && isBareOptionalValue(args[1])) {
      return new String[]{"open", "menu=" + args[1]};
    }

    if (args.length == 3
        && isGroup(args[0], "preview", "previews")
        && args[1].equalsIgnoreCase("reset")
        && isBareOptionalValue(args[2])) {
      return new String[]{"previews", "reset", "name=" + args[2]};
    }

    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("near")
        && isBareOptionalValue(args[2])) {
      return new String[]{"boards", "near", "radius=" + args[2]};
    }

    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("list")
        && isBareOptionalValue(args[2])) {
      return new String[]{"boards", "list", "page=" + args[2]};
    }

    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("create")
        && isBareOptionalValue(args[2])) {
      return new String[]{args[0], "create", args[2]};
    }

    if (args.length == 4
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("create")
        && isBareOptionalValue(args[3])) {
      return new String[]{args[0], "create", args[2], "menu=" + args[3]};
    }

    return args;
  }

  private static String[] normalizeTrailingText(String[] args) {
    if (args.length < 2
        || (!isGroup(args[0], "menu", "menus") && !isGroup(args[0], "board", "boards"))) {
      return args;
    }

    TrailingArgument trailing = switch (args[1].toLowerCase(Locale.ROOT)) {
      case "addrow" -> new TrailingArgument(3, "text");
      case "insertrow", "setrow" -> new TrailingArgument(4, "text");
      case "seticon", "style" -> new TrailingArgument(5, "value");
      case "image" -> new TrailingArgument(3, "path");
      default -> null;
    };
    if (trailing == null || args.length <= trailing.index()) {
      return args;
    }

    String prefix = trailing.name() + "=";
    String joined = String.join(" ", Arrays.copyOfRange(args, trailing.index(), args.length));
    String trailingArgument = joined.regionMatches(true, 0, prefix, 0, prefix.length())
        ? joined
        : prefix + joined;
    String[] normalized = Arrays.copyOf(args, trailing.index() + 1);
    normalized[trailing.index()] = trailingArgument;
    return normalized;
  }

  static String[] normalizeTabArgs(String[] args) {
    if (args.length == 2
        && args[0].equalsIgnoreCase("open")
        && isBareTabValue(args[1])) {
      return new String[]{"open", "menu=" + args[1]};
    }

    if (args.length == 3
        && isGroup(args[0], "preview", "previews")
        && args[1].equalsIgnoreCase("reset")
        && isBareTabValue(args[2])) {
      return new String[]{"previews", "reset", "name=" + args[2]};
    }

    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("near")
        && isBareTabValue(args[2])) {
      return new String[]{"boards", "near", "radius=" + args[2]};
    }

    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("list")
        && isBareTabValue(args[2])) {
      return new String[]{"boards", "list", "page=" + args[2]};
    }

    if (args.length == 4
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("create")
        && isBareTabValue(args[3])) {
      return new String[]{args[0], "create", args[2], "menu=" + args[3]};
    }

    return args;
  }

  static List<String> restorePositionalSuggestions(String[] args, List<String> suggestions) {
    String prefix = positionalPrefix(args);
    if (prefix == null) {
      return suggestions;
    }

    return suggestions.stream()
        .map(suggestion -> suggestion.startsWith(prefix) ? suggestion.substring(prefix.length()) : suggestion)
        .toList();
  }

  private static boolean isBareOptionalValue(String token) {
    if (token == null || token.isEmpty()) {
      return false;
    }
    if (token.indexOf('=') >= 0) {
      return false;
    }
    String lower = token.toLowerCase(Locale.ROOT);
    return !lower.equals("help")
        && !lower.equals("?")
        && !lower.startsWith("help=");
  }

  private static boolean isBareTabValue(String token) {
    return token != null && token.indexOf('=') < 0 && (token.isEmpty() || isBareOptionalValue(token));
  }

  private static String positionalPrefix(String[] args) {
    if (args.length == 2 && args[0].equalsIgnoreCase("open") && isBareTabValue(args[1])) {
      return "menu=";
    }
    if (args.length == 3
        && isGroup(args[0], "preview", "previews")
        && args[1].equalsIgnoreCase("reset")
        && isBareTabValue(args[2])) {
      return "name=";
    }
    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("near")
        && isBareTabValue(args[2])) {
      return "radius=";
    }
    if (args.length == 3
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("list")
        && isBareTabValue(args[2])) {
      return "page=";
    }
    if (args.length == 4
        && isGroup(args[0], "board", "boards")
        && args[1].equalsIgnoreCase("create")
        && isBareTabValue(args[3])) {
      return "menu=";
    }
    return null;
  }

  private static boolean isGroup(String value, String canonical, String alias) {
    return value.equalsIgnoreCase(canonical) || value.equalsIgnoreCase(alias);
  }

  private boolean sendHelpIfRequested(CommandSender sender, String[] args) {
    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(getDirector(), Arrays.asList(args), 9);
    if (page.isEmpty()) {
      return false;
    }

    deliverHelp(sender, page.get(), plugin.getLocalization().directorResolver());

    return true;
  }

  void deliverHelp(CommandSender sender, DirectorMiniMenu.DirectorHelpPage page, DirectorTextResolver resolver) {
    DirectorMiniMenu.deliver(sender, page, DirectorMiniMenu.Theme.fromDirectorTheme(theme), resolver);
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


  private record BukkitDirectorSender(
      CommandSender sender) implements DirectorSender {
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

  private record TrailingArgument(int index, String name) {
  }
}
