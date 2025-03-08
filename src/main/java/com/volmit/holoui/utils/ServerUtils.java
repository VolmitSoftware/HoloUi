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
package com.volmit.holoui.utils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Method;

public final class ServerUtils {

    public static CommandMap getCommandMap() {
        try {
            Server server = Bukkit.getServer();
            Method method = server.getClass().getDeclaredMethod("getCommandMap");
            method.setAccessible(true);
            return (CommandMap) method.invoke(server);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void syncCommands() {
        try {
            Server server = Bukkit.getServer();
            Method method = server.getClass().getDeclaredMethod("syncCommands");
            method.setAccessible(true);
            method.invoke(server);
        } catch (Throwable ignored) {
        }
    }
}
