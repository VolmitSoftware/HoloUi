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
import art.arcane.holoui.exceptions.MenuIconException;

import java.util.List;

public record AnimatedImageData(List<String> source,
                                int speed) implements MenuIconData {
  public MenuIconType getType() {
    return MenuIconType.ANIMATED_TEXT_IMAGE;
  }

  public List<String> requireSource() throws MenuIconException {
    if (source == null || source.isEmpty())
      throw new MenuIconException("Animated icon has no source frames");
    for (String frame : source) {
      if (frame == null || frame.isBlank())
        throw new MenuIconException("Animated icon has a frame without a path");
    }
    return source;
  }
}
