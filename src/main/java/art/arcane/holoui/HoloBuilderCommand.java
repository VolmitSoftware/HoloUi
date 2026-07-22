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
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Director(name = "builder", description = "HoloUI builder server controls", descriptionKey = "holoui.command.builder.root")
public class HoloBuilderCommand {
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

  @Director(name = "status", description = "Show whether the HoloUI builder service is running", descriptionKey = "holoui.command.builder.status")
  public void status(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = HoloCommand.ROOT_PERM + ".server";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (HoloUI.INSTANCE.getBuilderServer().isServerRunning()) {
      String host = HuiSettings.BUILDER_IP.value().equalsIgnoreCase("0.0.0.0") ? "localhost" : HuiSettings.BUILDER_IP.value();
      String url = host + ":" + HuiSettings.BUILDER_PORT.value();
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.BUILDER_RUNNING,
          MessageArgs.builder().untrusted("url", url).build()
      ));
    } else {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_NOT_RUNNING));
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_START_HINT));
    }
  }

  @Director(name = "start", description = "Start the HoloUI builder service", descriptionKey = "holoui.command.builder.start")
  public void start(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = HoloCommand.ROOT_PERM + ".server.start";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    BuilderServer server = HoloUI.INSTANCE.getBuilderServer();
    if (server.isServerRunning()) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_ALREADY_RUNNING));
      return;
    }

    SchedulerUtils.runAsync(HoloUI.INSTANCE, () -> {
      sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_STARTING));
      if (!server.prepareServer()) {
        sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_SETUP_FAILED));
        return;
      }

      server.startServer(HuiSettings.BUILDER_IP.value(), HuiSettings.BUILDER_PORT.value());
      runOnSender(sender, () -> status(sender));
    });
  }

  @Director(name = "stop", description = "Stop the HoloUI builder service", descriptionKey = "holoui.command.builder.stop")
  public void stop(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = HoloCommand.ROOT_PERM + ".server.stop";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    if (HoloUI.INSTANCE.getBuilderServer().stopServer()) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_STOPPED));
    } else {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.BUILDER_NOT_RUNNING));
    }
  }

  private void sendPermissionDenied(CommandSender sender, String permission) {
    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
        HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build()
    ));
  }
}
