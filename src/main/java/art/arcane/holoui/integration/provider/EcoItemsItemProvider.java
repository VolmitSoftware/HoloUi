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
import com.willfp.eco.core.items.CustomItem;
import com.willfp.eco.core.items.Items;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * EcoItems, resolved through the eco item registry rather than the {@code EcoItems} Kotlin object,
 * so only eco's own static API is touched. Ids are {@code ecoitems:my_item}; a bare id is namespaced
 * for the caller. eco lowercases the key internally, so ids are case insensitive.
 */
public final class EcoItemsItemProvider implements ItemProvider {

  private static final String NAMESPACE = "ecoitems";

  @Override
  public String id() {
    return "ecoitems";
  }

  @Override
  public String pluginName() {
    return "EcoItems";
  }

  @Override
  public ItemStack resolve(String itemId) {
    // Items.lookup never returns null, it hands back an empty testable item whose stack is AIR
    ItemStack stack = Items.lookup(namespaced(itemId)).getItem();
    if (stack == null || stack.getType().isAir()) {
      return null;
    }
    return stack.clone();
  }

  @Override
  public Collection<String> listIds() {
    return Items.getCustomItems().stream()
        .map(CustomItem::getKey)
        .filter(key -> key.getNamespace().equalsIgnoreCase(NAMESPACE))
        .map(key -> NAMESPACE + ":" + key.getKey())
        .toList();
  }

  private static String namespaced(String itemId) {
    return itemId.indexOf(':') < 0 ? NAMESPACE + ":" + itemId : itemId;
  }
}
