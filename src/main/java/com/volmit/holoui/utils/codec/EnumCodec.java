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
package com.volmit.holoui.utils.codec;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;

import java.util.Optional;

public record EnumCodec<E extends Enum<E> & EnumCodec.Values>(Class<E> clazz) implements PrimitiveCodec<E> {

    @Override
    public <T> DataResult<E> read(DynamicOps<T> dynamicOps, T t) {
        DataResult<String> res = dynamicOps.getStringValue(t);
        if (res.error().isPresent() || res.result().isEmpty())
            return DataResult.error(() -> "Unable to parse EnumCodec for \"" + clazz.getSimpleName() + "\": " + res.error().get().message());
        Optional<E> value = Values.getValue(clazz, res.result().get());
        return value.map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unable to parse EnumCodec for \"" + clazz.getSimpleName() + "\": Unknown enum value \"" + res.result().get() + "\""));
    }

    @Override
    public <T> T write(DynamicOps<T> dynamicOps, E e) {
        return dynamicOps.createString(e.getSerializedName());
    }

    public interface Values {

        static <E extends Enum<E> & Values> Optional<E> getValue(Class<E> clazz, String serializedName) {
            for (E e : clazz.getEnumConstants())
                if (e.getSerializedName().equalsIgnoreCase(serializedName))
                    return Optional.of(e);
            return Optional.empty();
        }

        String getSerializedName();
    }
}
