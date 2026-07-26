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
package art.arcane.holoui.service;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderValues;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;

import java.util.Objects;
import java.util.logging.Logger;

public final class HoloUiPlaceholderExpansion extends VolmitPlaceholderExpansion {
  private static final String IDENTIFIER = "holoui";
  private static final String REQUIRED_PLUGIN = "holoui";
  private static final String AUTHOR = "Volmit Software";
  private static final String VERSION = "1.0.0";
  private static final String MENU_OPEN = "menu.open";
  private static final String MENU_ID = "menu.id";

  public HoloUiPlaceholderExpansion(PlayerSnapshotStore<String> openMenus, Logger logger) {
    super(IDENTIFIER, AUTHOR, VERSION, REQUIRED_PLUGIN, registry(openMenus), logger);
  }

  static PlaceholderKeyRegistry registry(PlayerSnapshotStore<String> openMenus) {
    Objects.requireNonNull(openMenus, "openMenus");

    return PlaceholderKeyRegistry.builder()
        .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
        .key(MENU_OPEN, playerId -> PlaceholderValues.bool(openMenus.get(playerId) != null))
        .key(MENU_ID, playerId -> PlaceholderValues.text(openMenus.get(playerId)))
        .build();
  }
}
