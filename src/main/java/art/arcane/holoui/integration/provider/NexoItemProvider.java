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
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * Nexo. Ids are the bare yml key with no namespace, matched case sensitively. {@code NexoItems} is
 * a Kotlin object, but every entry point used here has a real JVM static bridge.
 */
public final class NexoItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "nexo";
  }

  @Override
  public String pluginName() {
    return "Nexo";
  }

  @Override
  public ItemStack resolve(String itemId) {
    ItemBuilder builder = NexoItems.itemFromId(itemId);
    if (builder == null) {
      return null;
    }
    try {
      ItemStack stack = builder.build();
      return stack == null ? null : stack.clone();
    } catch (Exception malformedItem) {
      // Nexo builds lazily, so a malformed yml entry only blows up here and not at itemFromId
      return null;
    }
  }

  @Override
  public boolean has(String itemId) {
    return NexoItems.exists(itemId);
  }

  @Override
  public Collection<String> listIds() {
    return List.copyOf(NexoItems.itemNames());
  }
}
