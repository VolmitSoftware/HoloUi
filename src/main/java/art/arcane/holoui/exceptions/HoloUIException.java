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
package art.arcane.holoui.exceptions;

import lombok.Getter;

public abstract class HoloUIException extends Exception {

  @Getter
  private final ComponentType type;

  public HoloUIException(ComponentType type, String message) {
    super(message);
    this.type = type;
  }

  public HoloUIException(ComponentType type, String format, Object... objects) {
    super(String.format(format, objects));
    this.type = type;
  }

  public enum ComponentType {
    ICON,
    COMPONENT,
    ACTION
  }
}
