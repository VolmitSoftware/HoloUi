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

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;

import java.util.Map;

public class RegistryCodec<K, V extends Serializable<K>> {

    private final Codec<V> valueCodec;
    private final Map<K, Codec<? extends V>> registry = Maps.newHashMap();

    public RegistryCodec(Codec<K> keyCodec) {
        this.valueCodec = keyCodec.dispatch(Serializable::serialize, registry::get);
    }

    public void register(K key, Codec<? extends V> codec) {
        registry.putIfAbsent(key, codec);
    }

    public Codec<V> getCodec() {
        return this.valueCodec;
    }
}
