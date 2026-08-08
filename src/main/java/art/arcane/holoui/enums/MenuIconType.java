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

import art.arcane.holoui.config.icon.AnimatedImageData;
import art.arcane.holoui.config.icon.CustomItemIconData;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.config.icon.TextImageIconData;
import art.arcane.volmlib.util.json.EnumType;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MenuIconType implements EnumType.Values<MenuIconData> {
  ITEM("item", ItemIconData.class),
  CUSTOM_ITEM("customItem", CustomItemIconData.class),
  ANIMATED_TEXT_IMAGE("animatedTextImage", AnimatedImageData.class),
  TEXT_IMAGE("textImage", TextImageIconData.class),
  TEXT("text", TextIconData.class),
  ITEM_STACK("itemStack", null);

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
