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
import art.arcane.holoui.service.HoloUiTelemetry;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.PacketUtils;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
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

  public static int totalCount() {
    return displayEntities.size();
  }

  public static int visibleCount() {
    return playerVisibility.size();
  }

  public static void spawn(UUID uuid, Player player) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    if (displayEntity == null || player == null)
      return;

    PacketUtils.send(player, displayEntity.spawn());
    playerVisibility.put(uuid, player);
    HoloUiTelemetry.countSpawnChurn();
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
    HoloUiTelemetry.countSpawnChurn();
  }

  public static void delete(UUID uuid) {
    if (!displayEntities.containsKey(uuid))
      return;

    despawn(uuid);
    displayEntities.remove(uuid);
    playerVisibility.remove(uuid);
  }

  public static void delete(UUID uuid, Player fallbackPlayer) {
    if (!displayEntities.containsKey(uuid))
      return;

    if (unsupportedVersion()) {
      displayEntities.remove(uuid);
      playerVisibility.remove(uuid);
      return;
    }

    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.remove(uuid);
    Player target = player == null ? fallbackPlayer : player;
    if (displayEntity != null && target != null) {
      PacketUtils.send(target, displayEntity.remove());
    }
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

  public static void orient(UUID uuid, float yaw, float pitch, float roll) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null)
      return;

    double halfRoll = Math.toRadians(roll) / 2.0D;
    displayEntity.yaw(yaw)
        .pitch(pitch)
        .leftRotation(new Quaternion4f(0F, 0F, (float) Math.sin(halfRoll), (float) Math.cos(halfRoll)));
    if (player == null) {
      return;
    }
    PacketUtils.send(player, displayEntity.rotate(yaw, pitch));
    PacketUtils.send(player, displayEntity.headLook());
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  public static void changeName(UUID uuid, Component name) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    if (!displayEntity.isTextDisplay())
      return;
    displayEntity.text(name == null ? Component.empty() : name);
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  public static void changeTextBackground(UUID uuid, int backgroundColor) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    if (!displayEntity.isTextDisplay())
      return;
    displayEntity.backgroundColor(backgroundColor);
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  public static void changeScale(UUID uuid, float x, float y, float z) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    displayEntity.scale(new Vector3f(x, y, z));
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  public static void changeTransform(UUID uuid, float x, float y, float z, Vector3f translation) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    displayEntity.scale(new Vector3f(x, y, z));
    displayEntity.translation(translation == null ? new Vector3f(0, 0, 0) : translation);
    PacketUtils.send(player, displayEntity.dataPacket());
  }

  public static void changeItem(UUID uuid, ItemStack itemStack) {
    if (unsupportedVersion())
      return;
    DisplayEntity displayEntity = displayEntities.get(uuid);
    Player player = playerVisibility.get(uuid);
    if (displayEntity == null || player == null)
      return;
    if (!displayEntity.isItemDisplay())
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
