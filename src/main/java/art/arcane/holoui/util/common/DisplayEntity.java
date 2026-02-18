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
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack;

@Data
@Accessors(fluent = true)
public class DisplayEntity {
  private final int id;
  @NonNull
  private final UUID uuid;
  @NonNull
  private EntityType entityType;
  private byte entityFlags = 0;
  private boolean noGravity = true;
  @NonNull
  private Vector3d location = new Vector3d();
  private float pitch = 0f, yaw = 0f, headYaw = 0f;
  private int interpolationDelay = 0;
  private int interpolationDuration = 0;
  private int teleportDuration = 0;
  @NonNull
  private Vector3f translation = new Vector3f(0, 0, 0);
  @NonNull
  private Vector3f scale = new Vector3f(1, 1, 1);
  @NonNull
  private Quaternion4f leftRotation = new Quaternion4f(0, 0, 0, 1);
  @NonNull
  private Quaternion4f rightRotation = new Quaternion4f(0, 0, 0, 1);
  private byte billboard = 0;
  private int brightness = -1;
  private float viewRange = 1f;
  private float shadowRadius = 0f;
  private float shadowStrength = 0f;
  private float width = 0f;
  private float height = 0f;
  private int glowColorOverride = -1;
  @NonNull
  private Component text = Component.empty();
  private int lineWidth = 200;
  private int backgroundColor = 0;
  private byte textOpacity = (byte) 0xFF;
  private byte textFlags = 0;
  @NonNull
  private ItemStack item = new ItemStack(Material.AIR);
  private byte itemDisplayType = 0;

  public List<PacketWrapper<?>> spawn() {
    List<PacketWrapper<?>> packets = new ArrayList<>();
    packets.add(new WrapperPlayServerSpawnEntity(id, Optional.of(uuid), entityType,
        location, pitch, yaw, headYaw, 0, Optional.empty()));
    packets.add(dataPacket());
    return packets;
  }

  public WrapperPlayServerDestroyEntities remove() {
    return new WrapperPlayServerDestroyEntities(id);
  }

  public PacketWrapper<?> goTo(@NonNull Location location) {
    this.location = PacketUtils.vector3d(location.toVector());
    this.pitch = location.getPitch();
    this.yaw = location.getYaw();
    return new WrapperPlayServerEntityTeleport(id, this.location, yaw, pitch, true);
  }

  public PacketWrapper<?> move(@NonNull Vector offset) {
    location = location.add(PacketUtils.vector3d(offset));
    return new WrapperPlayServerEntityTeleport(id, location, yaw, pitch, true);
  }

  public PacketWrapper<?> rotate(float yaw, float pitch) {
    this.yaw = yaw;
    this.pitch = pitch;
    return new WrapperPlayServerEntityTeleport(id, location, yaw, pitch, true);
  }

  public PacketWrapper<?> dataPacket() {
    List<EntityData<?>> metadata = new ArrayList<>();
    metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, entityFlags));
    metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, noGravity));

    metadata.add(new EntityData<>(8, EntityDataTypes.INT, interpolationDelay));
    metadata.add(new EntityData<>(9, EntityDataTypes.INT, interpolationDuration));
    metadata.add(new EntityData<>(10, EntityDataTypes.INT, teleportDuration));
    metadata.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, translation));
    metadata.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, scale));
    metadata.add(new EntityData<>(13, EntityDataTypes.QUATERNION, leftRotation));
    metadata.add(new EntityData<>(14, EntityDataTypes.QUATERNION, rightRotation));
    metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, billboard));
    metadata.add(new EntityData<>(16, EntityDataTypes.INT, brightness));
    metadata.add(new EntityData<>(17, EntityDataTypes.FLOAT, viewRange));
    metadata.add(new EntityData<>(18, EntityDataTypes.FLOAT, shadowRadius));
    metadata.add(new EntityData<>(19, EntityDataTypes.FLOAT, shadowStrength));
    metadata.add(new EntityData<>(20, EntityDataTypes.FLOAT, width));
    metadata.add(new EntityData<>(21, EntityDataTypes.FLOAT, height));
    metadata.add(new EntityData<>(22, EntityDataTypes.INT, glowColorOverride));

    if (entityType.equals(EntityTypes.TEXT_DISPLAY)) {
      metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));
      metadata.add(new EntityData<>(24, EntityDataTypes.INT, lineWidth));
      metadata.add(new EntityData<>(25, EntityDataTypes.INT, backgroundColor));
      metadata.add(new EntityData<>(26, EntityDataTypes.BYTE, textOpacity));
      metadata.add(new EntityData<>(27, EntityDataTypes.BYTE, textFlags));
    } else if (entityType.equals(EntityTypes.ITEM_DISPLAY)) {
      metadata.add(new EntityData<>(23, EntityDataTypes.ITEMSTACK, fromBukkitItemStack(item)));
      metadata.add(new EntityData<>(24, EntityDataTypes.BYTE, itemDisplayType));
    }

    return new WrapperPlayServerEntityMetadata(id, metadata);
  }

  public static final class Builder {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(Integer.MIN_VALUE);

    private final DisplayEntity displayEntity;

    private Builder(EntityType type) {
      this.displayEntity = new DisplayEntity(nextId(), UUID.randomUUID(), type);
    }

    public static DisplayEntity textDisplay(Component component, Location loc) {
      return textDisplay(component, loc, 1.00F);
    }

    public static DisplayEntity textDisplay(Component component, Location loc, float scale) {
      return textDisplay(component, loc, scale, (byte) 0, (byte) 0, 0);
    }

    public static DisplayEntity textDisplay(Component component, Location loc, float scale, byte billboard, byte textFlags, int backgroundColor) {
      return new Builder(EntityTypes.TEXT_DISPLAY)
          .text(component)
          .noGravity(true)
          .billboard(billboard)
          .shadow(0f, 0f)
          .textOpacity((byte) 0xFF)
          .lineWidth(2000)
          .backgroundColor(backgroundColor)
          .textFlags(textFlags)
          .scale(scale, scale, scale)
          .pos(loc)
          .build();
    }

    public static DisplayEntity itemDisplay(ItemStack stack, Location loc) {
      return itemDisplay(stack, loc, 1.00F);
    }

    public static DisplayEntity itemDisplay(ItemStack stack, Location loc, float scale) {
      return itemDisplay(stack, loc, scale, (byte) 0, (byte) 0);
    }

    public static DisplayEntity itemDisplay(ItemStack stack, Location loc, float scale, byte billboard, byte itemDisplayType) {
      return new Builder(EntityTypes.ITEM_DISPLAY)
          .item(stack)
          .noGravity(true)
          .billboard(billboard)
          .shadow(0f, 0f)
          .itemDisplayType(itemDisplayType)
          .scale(scale, scale, scale)
          .pos(loc)
          .build();
    }

    private static int nextId() {
      return NEXT_ID.getAndUpdate(i -> {
        if (++i < 0) return i;
        HoloUI.log(Level.SEVERE, "Entity IDs overflow");
        HoloUI.log(Level.SEVERE, "Please restart your server!");
        return Integer.MIN_VALUE;
      });
    }

    public Builder pos(Location loc) {
      displayEntity.location(PacketUtils.vector3d(loc.toVector()))
          .yaw(loc.getYaw())
          .pitch(loc.getPitch());
      return this;
    }

    public Builder rot(float pitch, float yaw) {
      displayEntity.pitch(pitch);
      displayEntity.yaw(yaw);
      return this;
    }

    public Builder noGravity(boolean noGravity) {
      displayEntity.noGravity(noGravity);
      return this;
    }

    public Builder billboard(byte billboard) {
      displayEntity.billboard(billboard);
      return this;
    }

    public Builder shadow(float radius, float strength) {
      displayEntity.shadowRadius(radius);
      displayEntity.shadowStrength(strength);
      return this;
    }

    public Builder text(Component component) {
      displayEntity.text(component == null ? Component.empty() : component);
      return this;
    }

    public Builder lineWidth(int width) {
      displayEntity.lineWidth(width);
      return this;
    }

    public Builder backgroundColor(int color) {
      displayEntity.backgroundColor(color);
      return this;
    }

    public Builder textOpacity(byte opacity) {
      displayEntity.textOpacity(opacity);
      return this;
    }

    public Builder textFlags(byte flags) {
      displayEntity.textFlags(flags);
      return this;
    }

    public Builder item(ItemStack stack) {
      displayEntity.item(stack == null ? new ItemStack(Material.AIR) : stack.clone());
      return this;
    }

    public Builder itemDisplayType(byte type) {
      displayEntity.itemDisplayType(type);
      return this;
    }

    public Builder scale(float x, float y, float z) {
      displayEntity.scale(new Vector3f(x, y, z));
      return this;
    }

    public DisplayEntity build() {
      return displayEntity;
    }
  }
}
