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

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.EitherCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.holoui.enums.MenuIconType;

import java.util.List;

public record AnimatedImageData(Either<String, List<String>> source, int speed) implements MenuIconData {

    public static final Codec<AnimatedImageData> CODEC = RecordCodecBuilder.create(i -> i.group(
            new EitherCodec<>(Codec.STRING, Codec.STRING.listOf()).fieldOf("source").forGetter(AnimatedImageData::source),
            Codec.INT.fieldOf("speed").forGetter(AnimatedImageData::speed)
    ).apply(i, AnimatedImageData::new));

    public MenuIconType getType() {
        return MenuIconType.ANIMATED_TEXT_IMAGE;
    }
}
