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

import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Director(name = "holoui", aliases = {"holo", "hui", "holou", "hu"}, description = "HoloUI command root")
public class HoloCommand {
  public static final String PREFIX = "[HoloUI]: ";
  public static final String ROOT_PERM = "holoui.command";

  private final HoloUI plugin;
  private HoloBuilderCommand builder;

  public HoloCommand(HoloUI plugin) {
    this.plugin = plugin;
  }

  @Director(name = "list", description = "List all configured menus you can open")
  public void list(@Param(name = "sender", contextual = true, description = "Command sender context") CommandSender sender) {
    if (!sender.hasPermission(ROOT_PERM + ".list")) {
      sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
      return;
    }

    if (plugin.getConfigManager().keys().isEmpty()) {
      sender.sendMessage(PREFIX + ChatColor.GRAY + "No menus are available.");
      return;
    }

    sender.sendMessage(ChatColor.GRAY + "----------+=== Menus ===+----------");
    for (String menu : plugin.getConfigManager().keys()) {
      TextComponent component = new TextComponent(ChatColor.GRAY + "  - " + ChatColor.WHITE + menu);
      component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/holoui open " + menu));
      sender.spigot().sendMessage(component);
    }
    sender.sendMessage(ChatColor.GRAY + "----------------------------------");
  }

  @Director(name = "open", description = "Open a menu by id, or show menu list when set to *")
  public void open(
      @Param(name = "menu", description = "Menu id to open (* shows all menus)", defaultValue = "*", customHandler = MenuNameHandler.class)
      String menuName,
      @Param(name = "sender", contextual = true, description = "Command sender context")
      CommandSender sender
  ) {
    if (!sender.hasPermission(ROOT_PERM + ".open")) {
      sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
      return;
    }

    if ("*".equals(menuName.trim())) {
      list(sender);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(PREFIX + ChatColor.RED + "Menus can only be opened by players.");
      return;
    }

    openMenu(player, sender, menuName, true);
  }

  @Director(name = "back", description = "Reopen your previous menu session")
  public void back(@Param(name = "sender", contextual = true, description = "Command sender context") CommandSender sender) {
    if (!sender.hasPermission(ROOT_PERM + ".back")) {
      sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(PREFIX + ChatColor.RED + "This command is only available to players.");
      return;
    }

    if (!plugin.getSessionManager().openLastSession(player)) {
      player.sendMessage(PREFIX + ChatColor.RED + "No previous menu is available.");
    }
  }

  @Director(name = "close", description = "Close your currently open menu session")
  public void close(@Param(name = "sender", contextual = true, description = "Command sender context") CommandSender sender) {
    if (!sender.hasPermission(ROOT_PERM + ".close")) {
      sender.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(PREFIX + ChatColor.RED + "This command is only available to players.");
      return;
    }

    if (plugin.getSessionManager().destroySession(player, false)) {
      player.sendMessage(PREFIX + ChatColor.GREEN + "Menu closed.");
    } else {
      player.sendMessage(PREFIX + ChatColor.RED + "No menu is currently open.");
    }
  }

  private boolean openMenu(Player player, CommandSender feedback, String menuName, boolean includeRootPermission) {
    MenuDefinitionData ui = plugin.getConfigManager().get(menuName).orElse(null);
    if (ui == null) {
      feedback.sendMessage(PREFIX + ChatColor.RED + "\"" + menuName + "\" is not available.");
      return false;
    }

    if (includeRootPermission && !player.hasPermission(ROOT_PERM + ".open")) {
      feedback.sendMessage(PREFIX + ChatColor.RED + "You lack permission.");
      return false;
    }

    if (!player.hasPermission("holoui.open." + ui.getId())) {
      feedback.sendMessage(PREFIX + ChatColor.RED + "You lack permission to open \"" + ui.getId() + "\".");
      return false;
    }

    try {
      plugin.getSessionManager().createNewSession(player, ui);
      return true;
    } catch (Throwable e) {
      HoloUI.logExceptionStack(true, e, "Error opening menu \"%s\".", ui.getId());
      feedback.sendMessage(PREFIX + ChatColor.RED + "Failed to open menu \"" + ui.getId() + "\".");
      return false;
    }
  }

  public static class MenuNameHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      KList<String> out = new KList<>();
      out.add("*");

      if (HoloUI.INSTANCE == null || HoloUI.INSTANCE.getConfigManager() == null) {
        return out;
      }

      out.addAll(HoloUI.INSTANCE.getConfigManager().keys());
      out.removeDuplicates();
      return out;
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.trim().isEmpty()) {
        throw new DirectorParsingException("Menu name cannot be empty");
      }

      String value = in.trim();
      if ("*".equals(value)) {
        return value;
      }

      for (String candidate : getPossibilities()) {
        if (candidate.equalsIgnoreCase(value)) {
          return candidate;
        }
      }

      return value;
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }
}
