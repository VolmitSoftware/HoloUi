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
package com.volmit.holoui.config.icon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.holoui.enums.MenuIconType;

public record TextImageIconData(String relativePath) implements MenuIconData {

    public static final Codec<TextImageIconData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("path").forGetter(TextImageIconData::relativePath)
    ).apply(i, TextImageIconData::new));

    public MenuIconType getType() {
        return MenuIconType.TEXT_IMAGE;
    }
}
