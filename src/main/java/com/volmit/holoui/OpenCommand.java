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

import com.google.common.collect.Lists;
import com.volmit.holoui.config.MenuDefinitionData;
import com.volmit.holoui.utils.SimpleCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class OpenCommand extends SimpleCommand {

    public OpenCommand(String name) {
        super(name, "Opens the " + name + " menu.", "/" + name, Lists.newArrayList());
        setPermission("hui.open." + name);
        setPermissionMessage(HoloCommand.PREFIX + ChatColor.RED + "You lack permission to open \"" + getName() + "\".");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(HoloCommand.PREFIX + ChatColor.RED + "Direct menus can only be executed by players.");
            return true;
        }
        Optional<MenuDefinitionData> data = HoloUI.INSTANCE.getConfigManager().get(getName());
        if (data.isEmpty()) {
            p.sendMessage(HoloCommand.PREFIX + ChatColor.RED + "\"" + getName() + "\" is not available.");
            return true;
        }
        HoloUI.INSTANCE.getSessionManager().createNewSession(p, data.get());
        return true;
    }

}
