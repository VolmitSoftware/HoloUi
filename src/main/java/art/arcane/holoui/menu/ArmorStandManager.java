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
package art.arcane.holoui.menu;

import art.arcane.holoui.util.common.ArmorStand;
import art.arcane.holoui.util.common.PacketUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArmorStandManager {

    private static final Map<UUID, ArmorStand> armorStands = new ConcurrentHashMap<>();
    private static final Map<UUID, Player> playerVisibility = new ConcurrentHashMap<>();

    public static UUID add(ArmorStand stand) {
        UUID uuid = UUID.randomUUID();
        armorStands.put(uuid, stand);
        return uuid;
    }

    public static void spawn(UUID uuid, Player p) {
        ArmorStand stand = armorStands.get(uuid);
        if (stand == null)
            return;

        PacketUtils.send(p, stand.spawn());
        playerVisibility.put(uuid, p);
    }

    public static void despawn(UUID uuid) {
        ArmorStand stand = armorStands.get(uuid);
        Player player = playerVisibility.remove(uuid);
        if (stand == null || player == null)
            return;
        PacketUtils.send(player, stand.remove());
    }

    public static void delete(UUID uuid) {
        if (!armorStands.containsKey(uuid))
            return;

        despawn(uuid);
        armorStands.remove(uuid);
        playerVisibility.remove(uuid);
    }

    public static Vector location(UUID uuid) {
        ArmorStand stand = armorStands.get(uuid);
        if (stand == null)
            return new Vector();

        return PacketUtils.vector(stand.location());
    }

    public static void goTo(UUID uuid, Location loc) {
        ArmorStand stand = armorStands.get(uuid);
        Player player = playerVisibility.get(uuid);
        if (stand == null || player == null)
            return;
        PacketUtils.send(player, stand.goTo(loc));
    }

    public static void move(UUID uuid, Vector offset) {
        ArmorStand stand = armorStands.get(uuid);
        Player player = playerVisibility.get(uuid);
        if (stand == null || player == null)
            return;
        PacketUtils.send(player, stand.move(offset));
    }

    public static void changeName(UUID uuid, Component name) {
        ArmorStand stand = armorStands.get(uuid);
        Player player = playerVisibility.get(uuid);
        if (stand == null || player == null)
            return;
        var packet = stand
                .displayName(name)
                .dataPacket();
        PacketUtils.send(player, packet);
    }

    public static void rotate(UUID uuid, float yaw) {
        ArmorStand stand = armorStands.get(uuid);
        Player player = playerVisibility.get(uuid);
        if (stand == null || player == null)
            return;
        PacketUtils.send(player, stand.rotate(yaw, stand.pitch()));
    }
}
