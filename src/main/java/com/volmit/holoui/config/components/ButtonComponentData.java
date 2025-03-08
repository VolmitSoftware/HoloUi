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
package com.volmit.holoui.config.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.holoui.config.action.MenuActionData;
import com.volmit.holoui.config.icon.MenuIconData;
import com.volmit.holoui.enums.MenuComponentType;

import java.util.List;

public record ButtonComponentData(float highlightMod, List<MenuActionData> actions,
                                  MenuIconData iconData) implements ComponentData {

    public static final Codec<ButtonComponentData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("highlightModifier", 1.0F).forGetter(ButtonComponentData::highlightMod),
            MenuActionData.CODEC.listOf().fieldOf("actions").forGetter(ButtonComponentData::actions),
            MenuIconData.CODEC.fieldOf("icon").forGetter(ButtonComponentData::iconData)
    ).apply(i, ButtonComponentData::new));

    public MenuComponentType getType() {
        return MenuComponentType.BUTTON;
    }
}
