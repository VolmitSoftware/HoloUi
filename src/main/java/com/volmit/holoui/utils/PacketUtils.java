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

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Collections;

public final class PacketUtils {

    public static void send(Player player, PacketWrapper<?> packet) {
        if (player == null) return;
        send(player, Collections.singletonList(packet));
    }

    public static void send(Player player, Collection<PacketWrapper<?>> packets) {
        if (player == null) return;
        send(Collections.singletonList(player), packets);
    }

    public static void send(Collection<Player> players, Collection<PacketWrapper<?>> packets) {
        var pm = PacketEvents.getAPI().getPlayerManager();
        players.forEach(player ->
                packets.forEach(packet ->
                        pm.sendPacket(player, packet)
                )
        );
    }

    public static Vector vector(Vector3d vector) {
        return new Vector(vector.getX(), vector.getY(), vector.getZ());
    }

    public static Vector3d vector3d(Vector vector) {
        return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
    }
}
