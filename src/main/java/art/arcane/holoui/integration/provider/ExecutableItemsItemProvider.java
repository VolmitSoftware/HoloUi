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
import com.ssomar.score.api.executableitems.ExecutableItemsAPI;
import com.ssomar.score.api.executableitems.config.ExecutableItemsManagerInterface;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ExecutableItems. Ids are the bare config file name. The API classes ship inside SCore, which
 * ExecutableItems hard depends on, so the ExecutableItems presence check covers both.
 */
public final class ExecutableItemsItemProvider implements ItemProvider {

  @Override
  public String id() {
    return "executableitems";
  }

  @Override
  public String pluginName() {
    return "ExecutableItems";
  }

  @Override
  public boolean isReady() {
    return ExecutableItemsAPI.getExecutableItemsManager() != null;
  }

  @Override
  public ItemStack resolve(String itemId) {
    ExecutableItemsManagerInterface manager = ExecutableItemsAPI.getExecutableItemsManager();
    if (manager == null) {
      return null;
    }
    ItemStack stack = manager.getExecutableItem(itemId)
        .map(item -> item.buildItem(1, Optional.empty()))
        .orElse(null);
    return stack == null ? null : stack.clone();
  }

  @Override
  public boolean has(String itemId) {
    ExecutableItemsManagerInterface manager = ExecutableItemsAPI.getExecutableItemsManager();
    return manager != null && manager.isValidID(itemId);
  }

  @Override
  public Collection<String> listIds() {
    ExecutableItemsManagerInterface manager = ExecutableItemsAPI.getExecutableItemsManager();
    return manager == null ? List.of() : List.copyOf(manager.getExecutableItemIdsList());
  }
}
