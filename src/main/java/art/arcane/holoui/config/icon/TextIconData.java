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
package art.arcane.holoui.config.icon;

import art.arcane.holoui.enums.MenuIconType;

public record TextIconData(String text, IconDisplayStyle style, Integer refreshTicks) implements MenuIconData {
  public static final int DEFAULT_REFRESH_TICKS = 10;
  public static final int MAX_REFRESH_TICKS = 1200;

  public TextIconData {
    if (refreshTicks != null && (refreshTicks < 0 || refreshTicks > MAX_REFRESH_TICKS)) {
      throw new IllegalArgumentException("refreshTicks must be between 0 and " + MAX_REFRESH_TICKS);
    }
  }

  public int resolvedRefreshTicks() {
    return refreshTicks == null ? DEFAULT_REFRESH_TICKS : refreshTicks;
  }

  public MenuIconType getType() {
    return MenuIconType.TEXT;
  }
}
