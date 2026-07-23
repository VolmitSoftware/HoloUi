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
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@Director(name = "holoui", aliases = {"holo", "hui", "holou", "hu"}, description = "HoloUI command root", descriptionKey = "holoui.command.root")
public class HoloCommand {
  public static final String ROOT_PERM = "holoui.command";

  private final HoloUI plugin;
  private HoloBuilderCommand builder;

  public HoloCommand(HoloUI plugin) {
    this.plugin = plugin;
  }

  @Director(name = "list", description = "List all configured menus you can open", descriptionKey = "holoui.command.list")
  public void list(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".list";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (plugin.getConfigManager().keys().isEmpty()) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_MENUS));
      return;
    }

    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(plugin.getLocalization().text(HoloMessages.MENU_LIST_HEADER), theme));
    for (String menu : plugin.getConfigManager().keys()) {
      String hover = plugin.getLocalization().text(
          HoloMessages.MENU_LIST_ENTRY,
          MessageArgs.builder().untrusted("menu", menu).build()
      );
      lines.add("<hover:show_text:'" + DirectorMiniMenu.escapeText(hover).replace("\\", "\\\\").replace("'", "\\'")
          + "'><click:run_command:/holoui open " + menu + ">"
          + "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
          + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(menu) + "</gradient>"
          + "</click></hover>");
    }
    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  @Director(name = "open", description = "Open a menu by id, or show the menu list when set to *", descriptionKey = "holoui.command.open")
  public void open(
      @Param(name = "menu", description = "Menu id to open (* shows all menus)", descriptionKey = "holoui.parameter.menu", defaultValue = "*", customHandler = MenuNameHandler.class)
      String menuName,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    String permission = ROOT_PERM + ".open";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if ("*".equals(menuName.trim())) {
      list(sender);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.MENUS_PLAYER_ONLY));
      return;
    }

    openMenu(player, sender, menuName, true);
  }

  @Director(name = "back", description = "Reopen your previous menu session", descriptionKey = "holoui.command.back")
  public void back(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".back";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.COMMAND_PLAYER_ONLY));
      return;
    }

    if (!plugin.getSessionManager().openLastSession(player)) {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_PREVIOUS_MENU));
    }
  }

  @Director(name = "close", description = "Close your currently open menu session", descriptionKey = "holoui.command.close")
  public void close(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = ROOT_PERM + ".close";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!(sender instanceof Player player)) {
      sender.sendMessage(plugin.getLocalization().legacy(HoloMessages.COMMAND_PLAYER_ONLY));
      return;
    }

    if (plugin.getSessionManager().destroySession(player, false)) {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.MENU_CLOSED));
    } else {
      player.sendMessage(plugin.getLocalization().legacy(HoloMessages.NO_OPEN_MENU));
    }
  }

  private boolean openMenu(Player player, CommandSender feedback, String menuName, boolean includeRootPermission) {
    MenuDefinitionData ui = plugin.getConfigManager().get(menuName).orElse(null);
    if (ui == null) {
      feedback.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_UNAVAILABLE,
          MessageArgs.builder().untrusted("menu", menuName).build()
      ));
      return false;
    }

    if (includeRootPermission && !player.hasPermission(ROOT_PERM + ".open")) {
      sendPermissionDenied(feedback, ROOT_PERM + ".open");
      return false;
    }

    if (!player.hasPermission("holoui.open." + ui.getId())) {
      feedback.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_PERMISSION_DENIED,
          MessageArgs.builder().untrusted("menu", ui.getId()).build()
      ));
      return false;
    }

    try {
      plugin.getSessionManager().createNewSession(player, ui);
      return true;
    } catch (Throwable e) {
      HoloUI.logExceptionStack(true, e, "Error opening menu \"%s\".", ui.getId());
      feedback.sendMessage(plugin.getLocalization().legacy(
          HoloMessages.MENU_OPEN_FAILED,
          MessageArgs.builder().untrusted("menu", ui.getId()).build()
      ));
      return false;
    }
  }

  private void sendPermissionDenied(CommandSender sender, String permission) {
    sender.sendMessage(plugin.getLocalization().legacy(
        HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build()
    ));
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
        throw new DirectorParsingException(HoloLocalization.globalText(HoloMessages.ERROR_MENU_NAME_REQUIRED));
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
