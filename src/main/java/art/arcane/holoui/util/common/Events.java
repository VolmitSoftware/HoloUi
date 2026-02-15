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
package art.arcane.holoui.util.common;

import art.arcane.holoui.HoloUI;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.function.Consumer;

public interface Events extends Listener, EventExecutor {

    static <T extends Event> Events listen(Class<T> type, Consumer<T> listener) {
        return listen(type, EventPriority.NORMAL, listener);
    }

    static <T extends Event> Events listen(Class<T> type, EventPriority priority, Consumer<T> listener) {
        final Events events = ($, event) -> {
            if (!type.isInstance(event)) return;
            listener.accept(type.cast(event));
        };
        Bukkit.getPluginManager().registerEvent(type, events, priority, events, HoloUI.INSTANCE);
        return events;
    }

    default void unregister() {
        HandlerList.unregisterAll(this);
    }
}