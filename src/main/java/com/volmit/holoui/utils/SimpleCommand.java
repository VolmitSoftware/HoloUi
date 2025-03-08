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

import com.google.common.collect.Lists;
import org.bukkit.command.Command;

import java.util.List;
import java.util.Optional;

public abstract class SimpleCommand extends Command {

    private static final List<SimpleCommand> REGISTRY = Lists.newArrayList();

    protected SimpleCommand(String name, String description, String usageMessage, List<String> aliases) {
        super(name, description, usageMessage, aliases);
    }

    public static boolean isRegistered(String name) {
        return getCommand(name).isPresent();
    }

    public static Optional<SimpleCommand> getCommand(String name) {
        return REGISTRY.stream().filter(cmd -> cmd.getName().equalsIgnoreCase(name)).findFirst();
    }

    public static boolean register(SimpleCommand cmd) {
        if (isRegistered(cmd.getName()))
            return false;
        if (ServerUtils.getCommandMap().register("holoui", cmd)) {
            REGISTRY.add(cmd);
            ServerUtils.syncCommands();
            return true;
        }
        return false;
    }

    public static boolean unregister(String name) {
        Optional<SimpleCommand> cmd = getCommand(name);
        if (cmd.isEmpty()) {
            return true;
        } else if (cmd.get().unregister(ServerUtils.getCommandMap())) {
            REGISTRY.remove(cmd.get());
            ServerUtils.syncCommands();
            return true;
        } else {
            return false;
        }
    }
}
