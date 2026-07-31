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
package art.arcane.holoui.integration;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Resolves an item id belonging to one third-party item plugin into a Bukkit {@link ItemStack}.
 * Implementations import their host plugin's API directly and are only ever class-loaded once that
 * plugin is confirmed present, so a missing host plugin can never cause a NoClassDefFoundError.
 */
public interface ItemProvider {

  /**
   * Lowercase, stable provider id. It is part of the menu file format and must never change.
   */
  String id();

  /**
   * Bukkit plugin name used for the presence check and for {@code softdepend} in plugin.yml.
   */
  String pluginName();

  /**
   * False while the host plugin is still populating its item registry. Resolution is skipped
   * entirely until this returns true, so a late-loading registry never poisons the prototype cache.
   */
  default boolean isReady() {
    return true;
  }

  /**
   * True when the host API may only be called from the main/region thread.
   */
  default boolean requiresMainThread() {
    return false;
  }

  /**
   * Returns a stack the caller owns, or null when the id is unknown. Must never throw.
   */
  ItemStack resolve(String itemId);

  default boolean has(String itemId) {
    return resolve(itemId) != null;
  }

  /**
   * Every id this provider can resolve, for the catalog dump. Empty when the host plugin offers no
   * enumeration or the registry is not ready yet.
   */
  Collection<String> listIds();

  default String displayName(String itemId) {
    return itemId;
  }
}
