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

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.DecoComponentData;
import art.arcane.holoui.config.icon.TextIconData;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class HopperPreview implements InventoryPreviewMenu<Inventory> {

    private static final float TITLE_Y = 0.66F;
    private static final float ROW_Y = 0.14F;
    private static final float SLOT_X_STEP = 0.44F;
    private static final int SLOT_COUNT = 5;

    @Override
    public void supply(Container b, List<MenuComponentData> components) {
        ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(b);
        Inventory inv = getInventory(b);
        components.add(component("header", 0F, TITLE_Y, 0F, new DecoComponentData(new TextIconData(theme.headerText()))));
        List<Integer> slots = InventoryPreviewLayout.visibleSlots(inv, SLOT_COUNT);
        if (slots.isEmpty()) {
            components.add(component("empty", 0F, ROW_Y, 0F, new DecoComponentData(new TextIconData("&8[ Empty ]"))));
            return;
        }
        float xStart = -((slots.size() - 1) * SLOT_X_STEP) / 2F;
        float frameRight = xStart + ((slots.size() - 1) * SLOT_X_STEP);
        InventoryPreviewLayout.addPanel(this, components, theme, "hop", xStart, frameRight, ROW_Y, ROW_Y, slots.size(), 1);
        for (int index = 0; index < slots.size(); index++) {
            float x = xStart + (index * SLOT_X_STEP);
            InventoryPreviewLayout.addSlot(this, components, theme, inv, slots.get(index), "_h" + index, x, ROW_Y);
        }
    }

    @Override
    public boolean isValid(Container b) {
        return b instanceof Hopper;
    }
}
