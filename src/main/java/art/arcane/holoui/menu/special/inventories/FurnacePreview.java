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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.block.Container;
import org.bukkit.inventory.FurnaceInventory;

import java.util.List;

public class FurnacePreview implements InventoryPreviewMenu<FurnaceInventory> {

    @Override
    public void supply(Container container, List<MenuComponentData> components) {
        FurnaceInventory inv = getInventory(container);
        components.add(component("cookProgress", 0, 0.65F, 0, new InventoryProgressComponent.Data(inv, i -> {
            FurnaceInventory furnace = (FurnaceInventory) i;
            return (double) furnace.getHolder().getCookTime() / (float) furnace.getHolder().getCookTimeTotal();
        }, 40, Style.style(NamedTextColor.WHITE))));
        components.add(component("input", -.8F, 0.25F, 0, new InventorySlotComponent.Data(inv, 0)));
        components.add(component("fuel", -.3F, 0.25F, 0, new InventorySlotComponent.Data(inv, 1)));
        components.add(component("progressArrow", .25F, 0.25F, 0, new DecoComponentData(new TextIconData("--->"))));
        components.add(component("output", .9F, 0.25F, 0, new InventorySlotComponent.Data(inv, 2)));
    }

    @Override
    public boolean isValid(Container b) {
        return b.getInventory() instanceof FurnaceInventory;
    }
}
