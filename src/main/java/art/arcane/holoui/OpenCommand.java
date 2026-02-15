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

import com.google.common.collect.Lists;
import art.arcane.holoui.util.project.command.SimpleCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class OpenCommand extends SimpleCommand {

    public OpenCommand(String name) {
        super(name, "Opens the " + name + " menu.", "/" + name, Lists.newArrayList());
        setPermission("holoui.open." + name);
        setPermissionMessage(HoloCommand.PREFIX + ChatColor.RED + "You lack permission to open \"" + getName() + "\".");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (HoloUI.INSTANCE.getCommandService() == null) {
            sender.sendMessage(HoloCommand.PREFIX + ChatColor.RED + "Commands are still loading.");
            return true;
        }

        return HoloUI.INSTANCE.getCommandService().openMenuFromAlias(sender, getName());
    }

}
