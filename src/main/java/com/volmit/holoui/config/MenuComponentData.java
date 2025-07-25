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
package com.volmit.holoui.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.holoui.config.components.ComponentData;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.menu.components.MenuComponent;
import com.volmit.holoui.utils.codec.Codecs;
import org.bukkit.util.Vector;

public record MenuComponentData(String id, Vector offset, ComponentData data) {
    public static final Codec<MenuComponentData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(MenuComponentData::id),
            Codecs.VECTOR.fieldOf("offset").forGetter(MenuComponentData::offset),
            ComponentData.CODEC.fieldOf("data").forGetter(MenuComponentData::data)
    ).apply(i, MenuComponentData::new));

    public MenuComponent<?> createComponent(MenuSession session) {
        return data.createComponent(session, this);
    }
}
