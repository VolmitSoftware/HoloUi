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
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * ItemsAdder. Ids are {@code namespace:id}, lowercase by ItemsAdder's own config validation.
 */
public final class ItemsAdderItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "itemsadder";
  }

  @Override
  public String pluginName() {
    return "ItemsAdder";
  }

  @Override
  public boolean isReady() {
    // ItemsAdder loads its items asynchronously long after the server starts, and an id looked up
    // before that point resolves to nothing and would otherwise be cached as a permanent miss.
    // ItemsAdder deprecates this in favour of its load event, but this is the only pull style probe.
    return ItemsAdder.areItemsLoaded();
  }

  @Override
  public ItemStack resolve(String itemId) {
    CustomStack custom = CustomStack.getInstance(itemId);
    if (custom == null) {
      return null;
    }
    ItemStack stack = custom.getItemStack();
    // the registry hands back its own live instance, so mutating it would corrupt every future lookup
    return stack == null ? null : stack.clone();
  }

  @Override
  public boolean has(String itemId) {
    return CustomStack.isInRegistry(itemId);
  }

  @Override
  public Collection<String> listIds() {
    return List.copyOf(CustomStack.getNamespacedIdsInRegistry());
  }

  @Override
  public String displayName(String itemId) {
    CustomStack custom = CustomStack.getInstance(itemId);
    if (custom == null) {
      return itemId;
    }
    String name = custom.getDisplayName();
    return name == null || name.isBlank() ? itemId : name;
  }
}
