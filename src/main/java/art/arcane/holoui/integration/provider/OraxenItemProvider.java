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
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Oraxen. Ids are the bare yml key with no namespace, matched case sensitively.
 */
public final class OraxenItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "oraxen";
  }

  @Override
  public String pluginName() {
    return "Oraxen";
  }

  @Override
  public ItemStack resolve(String itemId) {
    ItemBuilder builder = OraxenItems.getItemById(itemId);
    if (builder == null) {
      return null;
    }
    ItemStack stack = builder.build();
    return stack == null ? null : stack.clone();
  }

  @Override
  public boolean has(String itemId) {
    return OraxenItems.exists(itemId);
  }

  @Override
  public Collection<String> listIds() {
    return List.copyOf(OraxenItems.getNames());
  }
}
