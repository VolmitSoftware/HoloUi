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
package art.arcane.holoui.enums;

import art.arcane.holoui.config.icon.*;
import art.arcane.volmlib.util.json.EnumType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MenuIconType implements EnumType.Values<MenuIconData> {
  ITEM("item", ItemIconData.class),
  ANIMATED_TEXT_IMAGE("animatedTextImage", AnimatedImageData.class),
  TEXT_IMAGE("textImage", TextImageIconData.class),
  TEXT("text", TextIconData.class),
  FONT_IMAGE("fontImage", null);

  private final String value;
  private final Class<? extends MenuIconData> type;

  public String getSerializedName() {
    return value;
  }

  @Override
  public Class<? extends MenuIconData> getType() {
    return type;
  }
}
