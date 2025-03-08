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
import com.volmit.holoui.config.action.CommandActionData;
import com.volmit.holoui.config.action.MenuActionData;
import com.volmit.holoui.config.action.SoundActionData;
import com.volmit.holoui.utils.codec.CodecDispatcherEnum;
import com.volmit.holoui.utils.codec.EnumCodec;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MenuActionType implements EnumCodec.Values, CodecDispatcherEnum<MenuActionData> {

    COMMAND("command", CommandActionData.CODEC),
    SOUND("sound", SoundActionData.CODEC);

    public static final Codec<MenuActionType> CODEC = new EnumCodec<>(MenuActionType.class);

    private final String value;
    private final Codec<? extends MenuActionData> codec;

    public Codec<? extends MenuActionData> getCodec() {
        return codec;
    }

    public String getSerializedName() {
        return value;
    }
}
