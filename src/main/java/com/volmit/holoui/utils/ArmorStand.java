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
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.volmit.holoui.HoloUI;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static com.github.retrooper.packetevents.protocol.player.EquipmentSlot.*;
import static io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack;

@Data
@Accessors(fluent = true)
public class ArmorStand {
    private static final AtomicInteger dataIndex = new AtomicInteger();
    private final int id;
    @NonNull
    private final UUID uuid;
    @NonNull
    private final List<ArmorStand> passengers = new ArrayList<>();
    @NonNull
    private final Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();
    @Nullable
    private Component displayName = null;
    private boolean invisible = false;
    private boolean customNameVisible = false;
    @NonNull
    private Vector3d location = new Vector3d();
    private float pitch = 0f, yaw = 0f, headYaw = 0f;
    private boolean small = false;
    private boolean basePlate = false;
    private boolean marker = false;
    private boolean showArms = false;
    @NonNull
    private Vector3f headPose = new Vector3f(0, 0, 0);
    @NonNull
    private Vector3f bodyPose = new Vector3f(0, 0, 0);
    @NonNull
    private Vector3f leftArmPose = new Vector3f(-10, 0, 10);
    @NonNull
    private Vector3f rightArmPose = new Vector3f(-15, 0, 10);
    @NonNull
    private Vector3f leftLegPose = new Vector3f(1, 0, 1);
    @NonNull
    private Vector3f rightLegPose = new Vector3f(1, 0, 1);

    public List<PacketWrapper<?>> spawn() {
        List<PacketWrapper<?>> packets = new ArrayList<>();
        packets.add(new WrapperPlayServerSpawnEntity(id, Optional.of(uuid), EntityTypes.ARMOR_STAND,
                location, pitch, yaw, headYaw, 0, Optional.empty()));
        packets.add(dataPacket());

        if (!equipment.isEmpty()) {
            List<Equipment> list = new ArrayList<>();

            list.add(new Equipment(MAIN_HAND, fromBukkitItemStack(equipment.get(EquipmentSlot.HAND))));
            list.add(new Equipment(OFF_HAND, fromBukkitItemStack(equipment.get(EquipmentSlot.OFF_HAND))));
            list.add(new Equipment(HELMET, fromBukkitItemStack(equipment.get(EquipmentSlot.HEAD))));
            list.add(new Equipment(CHEST_PLATE, fromBukkitItemStack(equipment.get(EquipmentSlot.CHEST))));
            list.add(new Equipment(LEGGINGS, fromBukkitItemStack(equipment.get(EquipmentSlot.LEGS))));
            list.add(new Equipment(BOOTS, fromBukkitItemStack(equipment.get(EquipmentSlot.FEET))));

            packets.add(new WrapperPlayServerEntityEquipment(id, list));
        }

        if (!passengers.isEmpty()) {
            passengers.stream()
                    .map(ArmorStand::spawn)
                    .forEach(packets::addAll);
            packets.add(new WrapperPlayServerSetPassengers(id, passengers.stream().mapToInt(ArmorStand::id).toArray()));
        }
        return packets;
    }

    public WrapperPlayServerDestroyEntities remove() {
        List<Integer> ids = new ArrayList<>(passengers.size() + 1);

        ids.add(id);
        if (!passengers.isEmpty()) {
            Queue<ArmorStand> queue = new LinkedList<>(passengers);
            while (!queue.isEmpty()) {
                ArmorStand stand = queue.remove();
                queue.addAll(stand.passengers);
                ids.add(stand.id);
            }
        }

        return new WrapperPlayServerDestroyEntities(ids.stream().mapToInt(id -> id).toArray());
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
        List<EntityData> metadata = new ArrayList<>();

        metadata.add(new EntityData(0, EntityDataTypes.BYTE, (byte) (invisible ? 0x20 : 0)));
        metadata.add(new EntityData(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.ofNullable(displayName)));
        metadata.add(new EntityData(3, EntityDataTypes.BOOLEAN, customNameVisible));
        metadata.add(new EntityData(5, EntityDataTypes.BOOLEAN, true));

        byte mask = 0;

        mask = setBit(mask, 1, small);
        mask = setBit(mask, 4, showArms);
        mask = setBit(mask, 8, !basePlate);
        mask = setBit(mask, 16, marker);

        int dataIndex = dataIndex();
        metadata.add(new EntityData(dataIndex++, EntityDataTypes.BYTE, mask));
        metadata.add(new EntityData(dataIndex++, EntityDataTypes.ROTATION, headPose));
        metadata.add(new EntityData(dataIndex++, EntityDataTypes.ROTATION, bodyPose));
        metadata.add(new EntityData(dataIndex++, EntityDataTypes.ROTATION, leftArmPose));
        metadata.add(new EntityData(dataIndex++, EntityDataTypes.ROTATION, rightArmPose));
        metadata.add(new EntityData(dataIndex++, EntityDataTypes.ROTATION, leftLegPose));
        metadata.add(new EntityData(dataIndex, EntityDataTypes.ROTATION, rightLegPose));

        return new WrapperPlayServerEntityMetadata(id, metadata);
    }

    private static int dataIndex() {
        return dataIndex.updateAndGet(i -> {
            if (i != 0) return i;

            ServerVersion serverVersion = PacketEvents.getAPI().getServerManager().getVersion();
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_17))
                return 15;
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_15))
                return 14;
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_14))
                return 13;
            if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_10))
                return 11;
            return 10;
        });
    }

    private byte setBit(byte b0, int i, boolean flag) {
        if (flag) return (byte) (b0 | i);
        else return (byte) (b0 & ~i);
    }

    public static final class Builder {
        private static final AtomicInteger NEXT_ID = new AtomicInteger(Integer.MIN_VALUE);

        private final ArmorStand armorStand;

        public Builder() {
            this.armorStand = new ArmorStand(nextId(), UUID.randomUUID());
        }

        public static ArmorStand nametagArmorStand(Component component, Location loc) {
            return new Builder()
                    .marker(true)
                    .invisible(true).basePlate(false).zeroPose()
                    .name(component, true).pos(loc).small(false)
                    .build();
        }

        public static Builder itemArmorStand(ItemStack s, Location loc) {
            return new Builder()
                    .marker(true)
                    .invisible(true).basePlate(false).zeroPose()
                    .helmet(s)
                    .pos(loc);
        }

        public static Builder tinyItemArmorStand(ItemStack s, Location loc) {
            return new Builder()
                    .marker(true).small(true)
                    .invisible(true).basePlate(false).arms(true).zeroPose()
                    .mainHand(s)
                    .pos(loc);
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
            armorStand.location(PacketUtils.vector3d(loc.toVector()))
                    .yaw(loc.getYaw())
                    .pitch(loc.getPitch());
            return this;
        }

        public Builder rot(float x, float y) {
            armorStand.pitch(x);
            armorStand.yaw(y);
            return this;
        }

        public Builder small(boolean small) {
            armorStand.small(small);
            return this;
        }

        public Builder arms(boolean arms) {
            armorStand.showArms(arms);
            return this;
        }

        public Builder marker(boolean marker) {
            armorStand.marker(marker);
            return this;
        }

        public Builder invisible(boolean invisible) {
            armorStand.invisible(invisible);
            return this;
        }

        public Builder basePlate(boolean basePlate) {
            armorStand.basePlate(basePlate);
            return this;
        }

        public Builder zeroPose() {
            armorStand.pitch(0);
            armorStand.yaw(0);
            armorStand.headYaw(0);
            return headPose(0, 0, 0)
                    .bodyPose(0, 0, 0)
                    .leftArm(0, 0, 0)
                    .rightArm(0, 0, 0)
                    .leftLeg(0, 0, 0)
                    .rightLeg(0, 0, 0);
        }

        public Builder headPose(float x, float y, float z) {
            armorStand.headPose(new Vector3f(x, y, z));
            return this;
        }

        public Builder bodyPose(float x, float y, float z) {
            armorStand.bodyPose(new Vector3f(x, y, z));
            return this;
        }

        public Builder leftArm(float x, float y, float z) {
            armorStand.leftArmPose(new Vector3f(x, y, z));
            return this;
        }

        public Builder rightArm(float x, float y, float z) {
            armorStand.rightArmPose(new Vector3f(x, y, z));
            return this;
        }

        public Builder leftLeg(float x, float y, float z) {
            armorStand.leftLegPose(new Vector3f(x, y, z));
            return this;
        }

        public Builder rightLeg(float x, float y, float z) {
            armorStand.rightLegPose(new Vector3f(x, y, z));
            return this;
        }

        public Builder name(String name) {
            return name(name, true);
        }

        public Builder name(Component name) {
            return name(name, true);
        }

        public Builder name(String name, boolean visible) {
            return name(Component.text(name), visible);
        }

        public Builder name(Component name, boolean visible) {
            armorStand.displayName(name);
            armorStand.customNameVisible(visible);
            return this;
        }

        public Builder helmet(ItemStack stack) {
            return equipment(EquipmentSlot.HEAD, stack);
        }

        public Builder chestPlate(ItemStack stack) {
            return equipment(EquipmentSlot.CHEST, stack);
        }

        public Builder leggings(ItemStack stack) {
            return equipment(EquipmentSlot.LEGS, stack);
        }

        public Builder boots(ItemStack stack) {
            return equipment(EquipmentSlot.FEET, stack);
        }

        public Builder mainHand(ItemStack stack) {
            return equipment(EquipmentSlot.HAND, stack);
        }

        public Builder offHand(ItemStack stack) {
            return equipment(EquipmentSlot.OFF_HAND, stack);
        }

        private Builder equipment(EquipmentSlot slot, ItemStack stack) {
            armorStand.equipment().put(slot, stack);
            return this;
        }

        public ArmorStand build() {
            return armorStand;
        }
    }
}
