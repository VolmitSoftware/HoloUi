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

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.PacketUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class DisplayEntityManager {

  private static final Map<UUID, DisplayEntity> displayEntities = new ConcurrentHashMap<>();
  private static final Map<UUID, Player> playerVisibility = new ConcurrentHashMap<>();
  private static final AtomicBoolean unsupportedVersionWarning = new AtomicBoolean(false);

  public static UUID add(DisplayEntity displayEntity) {
    UUID uuid = UUID.randomUUID();
    displayEntities.put(uuid, displayEntity);
    return uuid;
  }

  public static void spawn(UUID uuid, Player player) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    if (displayEntity == null || player == null)
      return;

    PacketUtils.send(player, displayEntity.spawn());
    playerVisibility.put(uuid, player);
  }

  public static void despawn(UUID uuid) {
    if (unsupportedVersion()) {
      playerVisibility.remove(uuid);
      return;
    }
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.remove(uuid);
    if (displayEntity == null || player == null)
      return;
    PacketUtils.send(player, displayEntity.remove());
  }

  public static void delete(UUID uuid) {
    if (!displayEntities.containsKey(uuid))
      return;

    despawn(uuid);
    displayEntities.remove(uuid);
    playerVisibility.remove(uuid);
  }

  public static Vector location(UUID uuid) {
    DisplayEntity displayEntity = displayEntities.get(uuid);
    if (displayEntity == null)
      return new Vector();

    return PacketUtils.vector(displayEntity.location());
  }

  public static void goTo(UUID uuid, Location location) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    PacketUtils.send(player, displayEntity.goTo(location));
  }

  public static void move(UUID uuid, Vector offset) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    PacketUtils.send(player, displayEntity.move(offset));
  }

  public static void rotate(UUID uuid, float yaw) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    PacketUtils.send(player, displayEntity.rotate(yaw, displayEntity.pitch()));
  }

  public static void changeName(UUID uuid, Component name) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    if (!displayEntity.entityType().equals(EntityTypes.TEXT_DISPLAY))
      return;
    displayEntity.text(name == null ? Component.empty() : name);
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  public static void changeItem(UUID uuid, ItemStack itemStack) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    if (!displayEntity.entityType().equals(EntityTypes.ITEM_DISPLAY))
      return;
    displayEntity.item(itemStack == null ? new ItemStack(Material.AIR) : itemStack.clone());
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  private static boolean unsupportedVersion() {
    if (PacketEvents.getAPI() != null
        && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19_4)) {
      return false;
    }

    if (unsupportedVersionWarning.compareAndSet(false, true)) {
      HoloUI.log(Level.WARNING, "HoloUi display-entity renderer requires Minecraft 1.19.4 or newer.");
    }

    return true;
  }
}
