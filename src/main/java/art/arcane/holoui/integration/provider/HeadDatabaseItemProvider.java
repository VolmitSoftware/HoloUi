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
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import me.arcaniax.hdb.enums.CategoryEnum;
import me.arcaniax.hdb.object.head.Head;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * HeadDatabase. Ids are numeric strings such as {@code 7129}.
 */
public final class HeadDatabaseItemProvider implements ItemProvider {

  private static final String API_CLASS = "me.arcaniax.hdb.api.HeadDatabaseAPI";
  // heads are downloaded in the background, and the online player list is per player rather than a
  // stable catalog id, so neither of these categories belongs in an enumeration
  private static final List<CategoryEnum> SKIPPED_CATEGORIES = List.of(CategoryEnum.ONLINE_PLAYERS, CategoryEnum.DISABLED);

  private final HeadDatabaseAPI api;

  public HeadDatabaseItemProvider() throws ClassNotFoundException {
    // other plugins have shipped under the name HeadDatabase, so the plugin name alone is not proof
    // that this API exists, and the check has to happen before any HeadDatabaseAPI type is resolved
    Class.forName(API_CLASS, false, HeadDatabaseItemProvider.class.getClassLoader());
    this.api = new HeadDatabaseAPI();
  }

  @Override
  public String id() {
    return "headdatabase";
  }

  @Override
  public String pluginName() {
    return "HeadDatabase";
  }

  @Override
  public ItemStack resolve(String itemId) {
    try {
      ItemStack head = api.getItemHead(itemId);
      return head == null ? null : head.clone();
    } catch (NullPointerException databaseNotLoaded) {
      // HeadDatabase dereferences its head map instead of reporting a miss until the database lands
      return null;
    }
  }

  @Override
  public boolean has(String itemId) {
    try {
      return api.isHead(itemId);
    } catch (NullPointerException databaseNotLoaded) {
      return false;
    }
  }

  @Override
  public Collection<String> listIds() {
    List<String> ids = new ArrayList<>();
    for (CategoryEnum category : CategoryEnum.values()) {
      if (SKIPPED_CATEGORIES.contains(category)) {
        continue;
      }
      try {
        List<Head> heads = api.getHeads(category);
        if (heads == null) {
          continue;
        }
        for (Head head : heads) {
          if (head != null && head.id != null) {
            ids.add(head.id);
          }
        }
      } catch (NullPointerException databaseNotLoaded) {
        return List.of();
      }
    }
    return List.copyOf(ids);
  }
}
