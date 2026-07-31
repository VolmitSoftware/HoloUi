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
package art.arcane.holoui.integration.provider;

import art.arcane.holoui.integration.ItemProvider;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Slimefun. Ids are {@code UPPER_SNAKE_CASE} and are looked up by an exact map get, so they are
 * matched case sensitively.
 */
public final class SlimefunItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "slimefun";
  }

  @Override
  public String pluginName() {
    return "Slimefun";
  }

  @Override
  public ItemStack resolve(String itemId) {
    SlimefunItem item = SlimefunItem.getById(itemId);
    if (item == null) {
      return null;
    }
    ItemStack stack = item.getItem();
    // getItem hands back the live SlimefunItemStack the registry itself holds
    return stack == null ? null : stack.clone();
  }

  @Override
  public boolean has(String itemId) {
    return SlimefunItem.getById(itemId) != null;
  }

  @Override
  public Collection<String> listIds() {
    return Slimefun.getRegistry().getEnabledSlimefunItems().stream()
        .map(SlimefunItem::getId)
        .toList();
  }

  @Override
  public String displayName(String itemId) {
    SlimefunItem item = SlimefunItem.getById(itemId);
    if (item == null) {
      return itemId;
    }
    String name = item.getItemName();
    return name == null || name.isBlank() ? itemId : name;
  }
}
