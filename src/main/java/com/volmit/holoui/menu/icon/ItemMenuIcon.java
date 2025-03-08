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
package com.volmit.holoui.menu.icon;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.volmit.holoui.config.icon.ItemIconData;
import com.volmit.holoui.exceptions.MenuIconException;
import com.volmit.holoui.menu.ArmorStandManager;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.utils.ArmorStand;
import com.volmit.holoui.utils.ItemUtils;
import com.volmit.holoui.utils.math.CollisionPlane;
import com.volmit.holoui.utils.math.MathHelper;
import com.volmit.holoui.utils.registries.Materials;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class ItemMenuIcon extends MenuIcon<ItemIconData> {

    private static final float ITEM_OFFSET = 1F;
    private static final float BLOCK_OFFSET = -.95F;
    private static final List<Material> BLOCK_BLACKLIST = ImmutableList.of(
            Material.BARRIER, Material.LIGHT, Material.HOPPER, Material.TURTLE_EGG, Materials.GRASS, Material.TALL_GRASS,
            Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE, Material.GLASS_PANE, Material.POPPY, Material.DANDELION);
    private final ItemStack item;

    public ItemMenuIcon(MenuSession session, Location loc, ItemIconData data) throws MenuIconException {
        super(session, loc, data);
        this.item = new ItemUtils.Builder(data.materialType(), data.count() > 0 ? data.count() : 1)
                .modelData(data.customModelValue())
                .get();
    }

    public CollisionPlane createBoundingBox() {
        return new CollisionPlane(position.toVector().clone().subtract(new Vector(0, 0.05F, 0)), .75F, .75F);
    }

    protected List<UUID> createArmorStands(Location loc) {
        List<UUID> uuids = Lists.newArrayList();
        Location location = loc.clone();
        if (isBlock())
            location.add(0, BLOCK_OFFSET, 0);
        else
            location.subtract(0, ITEM_OFFSET + (item.getAmount() > 1 ? 0 : .09F), 0);
        ArmorStand.Builder builder = ArmorStand.Builder.itemArmorStand(item, location).small(true);
        uuids.add(ArmorStandManager.add(builder.build()));
        if (item.getAmount() > 1) {
            loc.add(0F, -NAMETAG_SIZE - .15F, 0);
            Component count = Component.text(item.getAmount());
            uuids.add(ArmorStandManager.add(ArmorStand.Builder.nametagArmorStand(count, loc)));
        }
        return uuids;
    }

    public void updateCount(int count) {
        if (armorStands.size() == 1 && count > 1) {
            ArmorStandManager.move(armorStands.get(0), new Vector(0, .09F, 0));
            UUID armorStand = ArmorStandManager.add(ArmorStand.Builder.nametagArmorStand(Component.text(count), position.clone().add(0F, -NAMETAG_SIZE - .37F, 0)));
            armorStands.add(armorStand);
            ArmorStandManager.spawn(armorStand, session.getPlayer());
        } else if (armorStands.size() == 2 && count < 2) {
            ArmorStandManager.move(armorStands.get(0), new Vector(0, -.09F, 0));
            ArmorStandManager.delete(armorStands.get(1));
            armorStands.remove(1);
        } else {
            ArmorStandManager.changeName(armorStands.get(1), Component.text(count));
        }
    }

    @Override
    public void spawn() {
        super.spawn();
        rotate((float) MathHelper.getRotationFromDirection(session.getPlayer().getEyeLocation().getDirection().multiply(-1F)).getY());
        Vector dir = session.getPlayer().getEyeLocation().getDirection();
        rotate(-(float) MathHelper.getRotationFromDirection(dir).getY());
    }

    @Override
    public void rotate(float yaw) {
        if (isBlock()) {
            Location offset = MathHelper.rotateAroundPoint(this.position.clone().add(0, BLOCK_OFFSET, .3F), this.position, 0, yaw);
            ArmorStandManager.goTo(armorStands.get(0), offset);
            super.rotate(-yaw + 180);
        } else
            super.rotate(-yaw + 180);
    }

    private boolean isBlock() {
        return item.getType().isBlock() && !BLOCK_BLACKLIST.contains(item.getType());
    }
}
