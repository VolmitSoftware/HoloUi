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

import com.volmit.holoui.config.MenuComponentData;
import com.volmit.holoui.config.components.ComponentData;
import com.volmit.holoui.config.icon.ItemIconData;
import com.volmit.holoui.enums.MenuComponentType;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.menu.components.MenuComponent;
import com.volmit.holoui.menu.icon.ItemMenuIcon;
import com.volmit.holoui.menu.icon.MenuIcon;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventorySlotComponent extends MenuComponent<InventorySlotComponent.Data> {

    private static final ItemStack MISSING = new ItemStack(Material.BARRIER);

    private ItemStack currentStack;

    public InventorySlotComponent(MenuSession session, MenuComponentData data) {
        super(session, data);
        ItemStack item = this.data.inventory().getItem(this.data.slotId());
        if (item == null)
            currentStack = MISSING;
        else
            currentStack = item.clone();
    }

    @Override
    protected void onTick() {
        ItemStack stack = data.inventory().getItem(data.slotId());
        if (stack == null && currentStack != MISSING) {
            this.currentStack = MISSING;
            updateDisplay();
            return;
        }

        if (stack != null && currentStack.isSimilar(stack) && currentStack.getAmount() != stack.getAmount()) {
            this.currentStack.setAmount(stack.getAmount());
            ((ItemMenuIcon) currentIcon).updateCount(stack.getAmount());
            return;
        }

        if (stack != null && !currentStack.equals(stack)) {
            if (stack.getType() == Material.AIR || stack.getAmount() < 1)
                this.currentStack = MISSING;
            else
                this.currentStack = stack.clone();
            updateDisplay();
        }
    }

    @Override
    protected MenuIcon<?> createIcon() {
        return MenuIcon.createIcon(session, getLocation(), ItemIconData.of(currentStack, true), this);
    }

    protected void onOpen() {
    }

    protected void onClose() {
    }

    private void updateDisplay() {
        if (currentIcon != null)
            this.currentIcon.remove();
        this.currentIcon = MenuIcon.createIcon(session, getLocation(), ItemIconData.of(currentStack, true), this);
        this.currentIcon.teleport(location.clone());
        this.currentIcon.spawn();
    }

    public record Data(Inventory inventory, int slotId) implements ComponentData {
        public MenuComponentType getType() {
            return null;
        }
    }
}
