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

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.volmit.holoui.utils.ItemUtils;
import com.volmit.holoui.utils.registries.RegistryUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

public final class Codecs {

    public static final Codec<NamespacedKey> NAMESPACED_KEY = Codec.STRING.xmap(NamespacedKey::fromString, NamespacedKey::toString);
    public static final Codec<Material> MATERIAL = NAMESPACED_KEY.xmap(ItemUtils::identifierToMaterial, ItemUtils::materialToIdentifier);
    public static final Codec<Vector> VECTOR = Codec.DOUBLE.listOf().xmap(l -> new Vector(l.get(0), l.get(1), l.get(2)), v -> Lists.newArrayList(v.getX(), v.getY(), v.getZ()));

    public static final Codec<Sound> SOUND = NAMESPACED_KEY.xmap(key -> {
        try {
            return RegistryUtil.find(Sound.class, key);
        } catch (Throwable e) {
            return Sound.BLOCK_GLASS_BREAK;
        }
    }, Sound::getKey);
}
