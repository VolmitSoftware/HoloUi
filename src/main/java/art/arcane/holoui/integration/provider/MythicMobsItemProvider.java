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
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.items.ItemExecutor;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * MythicMobs items. Ids are the bare item config name with no namespace.
 */
public final class MythicMobsItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "mythicmobs";
  }

  @Override
  public String pluginName() {
    return "MythicMobs";
  }

  @Override
  public boolean isReady() {
    return items() != null;
  }

  @Override
  public ItemStack resolve(String itemId) {
    ItemExecutor items = items();
    if (items == null) {
      return null;
    }
    ItemStack stack = items.getItemStack(itemId);
    return stack == null ? null : stack.clone();
  }

  @Override
  public boolean has(String itemId) {
    ItemExecutor items = items();
    return items != null && items.getItem(itemId).isPresent();
  }

  @Override
  public Collection<String> listIds() {
    ItemExecutor items = items();
    return items == null ? List.of() : List.copyOf(items.getItemNames());
  }

  private static ItemExecutor items() {
    // inst() is null between class load and MythicMobs finishing its own enable
    MythicBukkit mythic = MythicBukkit.inst();
    return mythic == null ? null : mythic.getItemManager();
  }
}
