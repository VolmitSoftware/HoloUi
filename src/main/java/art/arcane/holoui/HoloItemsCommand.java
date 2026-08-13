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
import art.arcane.holoui.integration.ProviderStatus;
import art.arcane.holoui.integration.catalog.CustomItemCatalogResult;
import art.arcane.holoui.integration.catalog.CustomItemCatalogWriter;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorTheme;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

@Director(name = "item", aliases = {"items"}, description = "Custom item provider tools", descriptionKey = "holoui.command.items.root")
public class HoloItemsCommand {

  private volatile CustomItemCatalogWriter writer;

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

  @Director(name = "status", description = "Show which custom item providers are active", descriptionKey = "holoui.command.items.status")
  public void status(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = HoloCommand.ROOT_PERM + ".items";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!HuiSettings.customItemsEnabled()) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.ITEMS_DISABLED));
      return;
    }

    List<ProviderStatus> statuses = HoloUI.INSTANCE.getItemProviders().providerStatuses();
    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(HoloUI.INSTANCE.getLocalization().text(HoloMessages.ITEMS_STATUS_HEADER), theme));

    int active = 0;
    for (ProviderStatus status : statuses) {
      if (status.active()) {
        active++;
      }
    }

    String summary = HoloUI.INSTANCE.getLocalization().text(
        HoloMessages.ITEMS_STATUS_SUMMARY,
        MessageArgs.builder().untrusted("active", active).untrusted("total", statuses.size()).build()
    );
    lines.add("<" + theme.description() + ">" + DirectorMiniMenu.escapeText(summary) + "</" + theme.description() + ">");

    for (ProviderStatus status : statuses) {
      String state = stateText(HoloUI.INSTANCE.getLocalization(), status);
      String hover = HoloUI.INSTANCE.getLocalization().text(
          HoloMessages.ITEMS_STATUS_ENTRY,
          MessageArgs.builder().untrusted("provider", status.id()).untrusted("plugin", status.pluginName()).build()
      );
      String color = stateColor(status, theme);
      lines.add("<hover:show_text:'" + DirectorMiniMenu.escapeText(hover).replace("\\", "\\\\").replace("'", "\\'") + "'>"
          + "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
          + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(status.id()) + "</gradient> "
          + "<" + color + ">" + DirectorMiniMenu.escapeText(state) + "</" + color + ">"
          + "</hover>");
    }

    if (sender.hasPermission(HoloCommand.ROOT_PERM + ".items.export")) {
      String hint = HoloUI.INSTANCE.getLocalization().text(HoloMessages.ITEMS_STATUS_HINT);
      lines.add("<hover:show_text:'" + DirectorMiniMenu.escapeText(hint).replace("\\", "\\\\").replace("'", "\\'") + "'>"
          + "<click:run_command:/holoui item export>"
          + "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
          + "<" + theme.optional() + ">/holoui item export</" + theme.optional() + ">"
          + "</click></hover>");
    }

    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  @Director(name = "export", description = "Export the custom item catalog for the web editor", descriptionKey = "holoui.command.items.export")
  public void export(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = HoloCommand.ROOT_PERM + ".items.export";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (!HuiSettings.customItemsEnabled()) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.ITEMS_DISABLED));
      return;
    }

    CustomItemCatalogWriter catalogWriter = writer();
    if (catalogWriter.isRunning()) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.ITEMS_EXPORT_BUSY));
      return;
    }

    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.ITEMS_EXPORT_STARTED));
    if (!catalogWriter.exportAsync(result -> report(sender, result))) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.ITEMS_EXPORT_BUSY));
    }
  }

  private CustomItemCatalogWriter writer() {
    CustomItemCatalogWriter local = writer;
    if (local != null) {
      return local;
    }

    synchronized (this) {
      if (writer == null) {
        writer = new CustomItemCatalogWriter(HoloUI.INSTANCE, HoloUI.INSTANCE.getItemProviders(), HoloUI.INSTANCE.getDataFolder());
      }
      return writer;
    }
  }

  private void report(CommandSender sender, CustomItemCatalogResult result) {
    if (!result.success()) {
      sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.ITEMS_EXPORT_FAILED));
      playSound(sender, false);
      return;
    }

    if (result.itemCount() == 0) {
      sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.ITEMS_EXPORT_EMPTY,
          MessageArgs.builder().untrusted("path", result.path()).build()
      ));
      playSound(sender, true);
      return;
    }

    sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(
        HoloMessages.ITEMS_EXPORT_DONE,
        MessageArgs.builder()
            .untrusted("count", result.itemCount())
            .untrusted("providers", result.providerCount())
            .untrusted("path", result.path())
            .build()
    ));
    playSound(sender, true);
  }

  // the export finishes long after Director reported the command as handled, so it feeds back its own sound
  private void playSound(CommandSender sender, boolean success) {
    if (!(sender instanceof Player player)) {
      return;
    }

    DirectorTheme theme = DirectorThemes.forProduct(DirectorProduct.HOLOUI);
    runOnSender(sender, () -> player.playSound(player.getLocation(),
        success ? theme.getSuccessSound() : theme.getErrorSound(),
        SoundCategory.MASTER, 0.8f, success ? 1.3f : 0.85f));
  }

  private static TextKey stateKey(ProviderStatus status) {
    if (!status.pluginPresent()) {
      return HoloMessages.ITEMS_STATE_MISSING;
    }
    if (!status.active()) {
      return HoloMessages.ITEMS_STATE_INACTIVE;
    }
    return status.ready() ? HoloMessages.ITEMS_STATE_READY : HoloMessages.ITEMS_STATE_LOADING;
  }

  static String stateText(HoloLocalization localization, ProviderStatus status) {
    TextKey key = stateKey(status);
    if (key != HoloMessages.ITEMS_STATE_READY) {
      return localization.text(key);
    }
    return localization.text(
        key,
        MessageArgs.builder().untrusted("count", status.itemCount()).build()
    );
  }

  private static String stateColor(ProviderStatus status, DirectorMiniMenu.Theme theme) {
    if (!status.pluginPresent()) {
      return theme.muted();
    }
    if (!status.active()) {
      return theme.required();
    }
    return status.ready() ? theme.optional() : theme.description();
  }

  private void sendPermissionDenied(CommandSender sender, String permission) {
    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
        HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build()
    ));
  }
}
