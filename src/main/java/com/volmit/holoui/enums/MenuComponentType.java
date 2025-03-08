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
package com.volmit.holoui.enums;

import com.mojang.serialization.Codec;
import com.volmit.holoui.config.components.ButtonComponentData;
import com.volmit.holoui.config.components.ComponentData;
import com.volmit.holoui.config.components.DecoComponentData;
import com.volmit.holoui.config.components.ToggleComponentData;
import com.volmit.holoui.utils.codec.CodecDispatcherEnum;
import com.volmit.holoui.utils.codec.EnumCodec;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MenuComponentType implements EnumCodec.Values, CodecDispatcherEnum<ComponentData> {
    BUTTON("button", ButtonComponentData.CODEC),
    DECO("decoration", DecoComponentData.CODEC),
    TOGGLE("toggle", ToggleComponentData.CODEC);

    public static final Codec<MenuComponentType> CODEC = new EnumCodec<>(MenuComponentType.class);

    private final String serializedName;
    private final Codec<? extends ComponentData> codec;

    public Codec<? extends ComponentData> getCodec() {
        return codec;
    }

    public String getSerializedName() {
        return serializedName;
    }
}
