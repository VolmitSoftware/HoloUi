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
import org.bukkit.inventory.FurnaceInventory;

import java.util.List;

public class FurnacePreview implements InventoryPreviewMenu<FurnaceInventory> {

  @Override
  public void supply(Container container, List<MenuComponentData> components) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(container);
    FurnaceInventory inv = getInventory(container);
    components.add(component("header", 0F, 0.98F, 0F, new DecoComponentData(new TextIconData(theme.headerText()))));
    components.add(component("cookProgress", 0F, 0.66F, 0F, new InventoryProgressComponent.Data(inv, i -> {
      FurnaceInventory furnace = (FurnaceInventory) i;
      return (double) furnace.getHolder().getCookTime() / (float) furnace.getHolder().getCookTimeTotal();
    }, 28, theme.progressStyle())));
    InventoryPreviewLayout.addPanel(this, components, theme, "cooker", -1.00F, 0.96F, 0.16F, 0.16F, 3, 1);
    InventoryPreviewLayout.addSlot(this, components, theme, inv, 0, "_f0", -1.00F, 0.16F);
    InventoryPreviewLayout.addSlot(this, components, theme, inv, 1, "_f1", -0.46F, 0.16F);
    components.add(component("progressArrow", 0.14F, 0.18F, 0F, new DecoComponentData(new TextIconData(theme.arrowText()))));
    InventoryPreviewLayout.addSlot(this, components, theme, inv, 2, "_f2", 0.96F, 0.16F);
  }

  @Override
  public boolean isValid(Container b) {
    return b.getInventory() instanceof FurnaceInventory;
  }
}
