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
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * CraftEngine. Ids are {@code namespace:id}; a bare id is also accepted and resolved by a
 * cross-namespace path search, so both forms are passed through verbatim.
 */
public final class CraftEngineItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "craftengine";
  }

  @Override
  public String pluginName() {
    return "CraftEngine";
  }

  @Override
  public ItemStack resolve(String itemId) {
    BukkitItemDefinition definition = CraftEngineItems.byId(itemId);
    if (definition == null) {
      return null;
    }
    ItemStack stack = definition.buildBukkitItem();
    return stack == null ? null : stack.clone();
  }

  @Override
  public boolean has(String itemId) {
    return CraftEngineItems.byId(itemId) != null;
  }

  @Override
  public Collection<String> listIds() {
    return CraftEngineItems.loadedItems().keySet().stream()
        .map(Key::asString)
        .toList();
  }
}
