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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.volmit.holoui.enums.MenuIconType;
import com.volmit.holoui.utils.codec.Codecs;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ItemIconData(Material materialType, int count, int customModelValue) implements MenuIconData {

    public static final Codec<ItemIconData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.MATERIAL.fieldOf("item").forGetter(ItemIconData::materialType),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemIconData::count),
            Codec.INT.optionalFieldOf("customModelData", 0).forGetter(ItemIconData::customModelValue)
    ).apply(i, ItemIconData::new));

    public static ItemIconData of(ItemStack stack, boolean facing) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasCustomModelData())
            return new ItemIconData(stack.getType(), stack.getAmount(), stack.getItemMeta().getCustomModelData());
        else
            return new ItemIconData(stack.getType(), stack.getAmount(), 0);
    }

    public MenuIconType getType() {
        return MenuIconType.ITEM;
    }
}