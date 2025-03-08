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
package com.volmit.holoui.menu.action;

import com.volmit.holoui.config.action.CommandActionData;
import com.volmit.holoui.enums.MenuActionCommandSource;
import com.volmit.holoui.menu.MenuSession;
import org.bukkit.Bukkit;

public class CommandMenuAction extends MenuAction<CommandActionData> {

    public CommandMenuAction(CommandActionData data) {
        super(data);
    }

    @Override
    public void execute(MenuSession session) {
        String command = data.command().startsWith("/") ? data.command().substring(1) : data.command();
        if (data.source() == MenuActionCommandSource.PLAYER)
            session.getPlayer().performCommand(command);
        else
            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
    }
}
