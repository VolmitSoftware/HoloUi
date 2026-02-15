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

import com.google.common.collect.Lists;
import art.arcane.holoui.config.MenuComponentData;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.inventory.Inventory;

import java.util.List;

public class DispenserMenu implements InventoryPreviewMenu<Inventory> {

    private static final float X_START = -.5F;

    @Override
    public void supply(Container b, List<MenuComponentData> components) {
        Inventory inv = getInventory(b);
        components.addAll(getLine(inv, 0, .75F));
        components.addAll(getLine(inv, 3, .25F));
        components.addAll(getLine(inv, 6, -.25F));
    }

    @Override
    public boolean isValid(Container b) {
        return b instanceof Dispenser || b instanceof Dropper;
    }

    private List<MenuComponentData> getLine(Inventory inv, int startIndex, float yOffset) {
        List<MenuComponentData> line = Lists.newArrayList();
        for (int i = 0; i < 3; i++)
            line.add(component("slot" + (i + startIndex), X_START + (i * .5F), yOffset, 0, new InventorySlotComponent.Data(inv, i + startIndex)));
        return line;
    }
}
