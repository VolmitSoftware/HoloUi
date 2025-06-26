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
import com.volmit.holoui.utils.codec.Codecs;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.util.Vector;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class MenuDefinitionData {

    public static final Codec<MenuDefinitionData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.VECTOR.fieldOf("offset").forGetter(MenuDefinitionData::getOffset),
            Codec.BOOL.optionalFieldOf("lockPosition", true).forGetter(MenuDefinitionData::isFreeze),
            Codec.BOOL.optionalFieldOf("followPlayer", true).forGetter(MenuDefinitionData::isFollow),
            Codec.DOUBLE.optionalFieldOf("maxDistance", 100d).forGetter(MenuDefinitionData::getMaxDistance),
            Codec.BOOL.optionalFieldOf("closeOnDeath", true).forGetter(MenuDefinitionData::isCloseOnDeath),
            Codec.BOOL.optionalFieldOf("closeOnTeleport", false).forGetter(MenuDefinitionData::isCloseOnTeleport),
            MenuComponentData.CODEC.listOf().fieldOf("components").forGetter(MenuDefinitionData::getComponentData)
    ).apply(i, MenuDefinitionData::new));
    private final Vector offset;
    private final boolean freeze, follow;
    private final double maxDistance;
    private final boolean closeOnDeath, closeOnTeleport;
    private final List<MenuComponentData> componentData;
    @Setter
    private String id;
}
