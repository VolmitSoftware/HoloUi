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
package com.volmit.holoui.config.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.holoui.enums.MenuActionType;
import com.volmit.holoui.enums.SoundSource;
import com.volmit.holoui.utils.codec.Codecs;
import org.bukkit.Sound;

public record SoundActionData(Sound sound, SoundSource source, float volume, float pitch) implements MenuActionData {

    public static final Codec<SoundActionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.SOUND.fieldOf("sound").forGetter(SoundActionData::sound),
            SoundSource.CODEC.optionalFieldOf("source", SoundSource.MASTER).forGetter(SoundActionData::source),
            Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(SoundActionData::volume),
            Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(SoundActionData::pitch)
    ).apply(i, SoundActionData::new));

    public MenuActionType getType() {
        return MenuActionType.SOUND;
    }
}
