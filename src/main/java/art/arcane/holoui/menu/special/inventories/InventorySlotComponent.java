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
package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.enums.MenuComponentType;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.icon.ItemMenuIcon;
import art.arcane.holoui.menu.icon.MenuIcon;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InventorySlotComponent extends MenuComponent<InventorySlotComponent.Data> {

    private ItemStack currentStack;

    public InventorySlotComponent(MenuSession session, MenuComponentData data) {
        super(session, data);
        ItemStack item = this.data.inventory().getItem(this.data.slotId());
        if (isMissing(item))
            currentStack = missingStack();
        else
            currentStack = item.clone();
    }

    @Override
    protected void onTick() {
        ItemStack stack = data.inventory().getItem(data.slotId());
        if (isMissing(stack)) {
            if (isMissing(currentStack))
                return;
            this.currentStack = missingStack();
            updateDisplay();
            return;
        }

        if (currentStack.isSimilar(stack) && currentStack.getAmount() != stack.getAmount()) {
            this.currentStack.setAmount(stack.getAmount());
            ((ItemMenuIcon) currentIcon).updateCount(stack.getAmount());
            return;
        }

        if (!currentStack.equals(stack)) {
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

    private boolean isMissing(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1;
    }

    private ItemStack missingStack() {
        if (!HuiSettings.showPreviewEmptySlots())
            return new ItemStack(Material.AIR);
        return HuiSettings.previewEmptySlotItem();
    }

    public record Data(Inventory inventory, int slotId) implements ComponentData {
        public MenuComponentType getType() {
            return null;
        }

        @Override
        public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
            return new InventorySlotComponent(session, data);
        }
    }
}
