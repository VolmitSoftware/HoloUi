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
import com.volmit.holoui.config.MenuComponentData;
import com.volmit.holoui.config.icon.MenuIconData;
import com.volmit.holoui.enums.MenuComponentType;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.menu.components.DecoComponent;
import com.volmit.holoui.menu.components.MenuComponent;

public record DecoComponentData(MenuIconData iconData) implements ComponentData {

    public static final Codec<DecoComponentData> CODEC = RecordCodecBuilder.create(i -> i.group(
            MenuIconData.CODEC.fieldOf("icon").forGetter(DecoComponentData::iconData)
    ).apply(i, DecoComponentData::new));

    public MenuComponentType getType() {
        return MenuComponentType.DECO;
    }

    @Override
    public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
        return new DecoComponent(session, data);
    }
}
