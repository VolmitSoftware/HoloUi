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
import art.arcane.holoui.config.components.DecoComponentData;
import art.arcane.holoui.config.icon.TextIconData;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class ChestLikePreview implements InventoryPreviewMenu<Inventory> {

    private static final float TITLE_Y = 1.00F;
    private static final float GRID_TOP_Y = 0.52F;
    private static final float GRID_X_STEP = 0.44F;
    private static final float GRID_Y_STEP = 0.44F;

    @Override
    public void supply(Container b, List<MenuComponentData> components) {
        ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(b);
        Inventory inv = getInventory(b);
        if (inv instanceof DoubleChestInventory) {
            if (((org.bukkit.block.data.type.Chest) b.getBlockData()).getType() == org.bukkit.block.data.type.Chest.Type.LEFT)
                inv = ((DoubleChestInventory) inv).getRightSide();
            else
                inv = ((DoubleChestInventory) inv).getLeftSide();
        }
        components.add(component("header", 0F, TITLE_Y, 0F, new DecoComponentData(new TextIconData(theme.headerText()))));
        addGrid(inv, components, theme, 9, 3, GRID_TOP_Y);
    }

    @Override
    public boolean isValid(Container b) {
        return b instanceof Chest || b instanceof Barrel || b instanceof ShulkerBox;
    }

    private void addGrid(Inventory inventory, List<MenuComponentData> components, ContainerPreviewTheme theme, int columns, int rows, float topY) {
        List<Integer> slots = InventoryPreviewLayout.visibleSlots(inventory, columns * rows);
        if (slots.isEmpty()) {
            components.add(component("empty", 0F, topY, 0F, new DecoComponentData(new TextIconData("&8[ Empty ]"))));
            return;
        }
        int displayedRows = rows;
        if (!HuiSettings.showPreviewEmptySlots()) {
            displayedRows = Math.max(1, (int) Math.ceil(slots.size() / (double) columns));
        }
        float rowShift = ((rows - displayedRows) * GRID_Y_STEP) / 2F;
        float xStart = -((columns - 1) * GRID_X_STEP) / 2F;
        int displayedSlots = Math.min(slots.size(), displayedRows * columns);
        float frameTop = topY + rowShift;
        float frameBottom = frameTop - ((displayedRows - 1) * GRID_Y_STEP);
        float frameRight = xStart + ((columns - 1) * GRID_X_STEP);
        InventoryPreviewLayout.addPanel(this, components, theme, "chest", xStart, frameRight, frameTop, frameBottom, columns, displayedRows);
        for (int index = 0; index < displayedSlots; index++) {
            int row = index / columns;
            int column = index % columns;
            int slot = slots.get(index);
            float x = xStart + (column * GRID_X_STEP);
            float y = (topY + rowShift) - (row * GRID_Y_STEP);
            InventoryPreviewLayout.addSlot(this, components, theme, inventory, slot, "_c" + index, x, y);
        }
    }
}
