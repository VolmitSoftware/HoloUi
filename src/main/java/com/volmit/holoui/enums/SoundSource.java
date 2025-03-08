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
import com.volmit.holoui.utils.codec.EnumCodec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.SoundCategory;

import java.util.Locale;

@AllArgsConstructor
@Getter
public enum SoundSource implements EnumCodec.Values {
    MASTER(SoundCategory.MASTER),
    MUSIC(SoundCategory.MUSIC),
    RECORD(SoundCategory.RECORDS),
    WEATHER(SoundCategory.WEATHER),
    BLOCK(SoundCategory.BLOCKS),
    HOSTILE(SoundCategory.HOSTILE),
    NEUTRAL(SoundCategory.NEUTRAL),
    PLAYER(SoundCategory.PLAYERS),
    AMBIENT(SoundCategory.AMBIENT),
    VOICE(SoundCategory.VOICE);

    public static final Codec<SoundSource> CODEC = new EnumCodec<>(SoundSource.class);

    private final SoundCategory category;

    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
