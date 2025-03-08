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
package com.volmit.holoui.menu.special.inventories;

import com.google.common.collect.Lists;
import com.volmit.holoui.config.MenuComponentData;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class ChestLikePreview implements InventoryPreviewMenu<Inventory> {

    private static final float X_START = -2F;

    @Override
    public void supply(Container b, List<MenuComponentData> components) {
        Inventory inv = getInventory(b);
        if (inv instanceof DoubleChestInventory) {
            if (((org.bukkit.block.data.type.Chest) b.getBlockData()).getType() == org.bukkit.block.data.type.Chest.Type.LEFT)
                inv = ((DoubleChestInventory) inv).getRightSide();
            else
                inv = ((DoubleChestInventory) inv).getLeftSide();
        }
        components.addAll(getLine(inv, 0, .75F));
        components.addAll(getLine(inv, 9, .25F));
        components.addAll(getLine(inv, 18, -.25F));
    }

    @Override
    public boolean isValid(Container b) {
        return b instanceof Chest || b instanceof Barrel || b instanceof ShulkerBox;
    }

    private List<MenuComponentData> getLine(Inventory inv, int startIndex, float yOffset) {
        List<MenuComponentData> line = Lists.newArrayList();
        for (int i = 0; i < 9; i++)
            line.add(component("slot" + (i + startIndex), X_START + (i * .5F), yOffset, 0, new InventorySlotComponent.Data(inv, i + startIndex)));
        return line;
    }
}
