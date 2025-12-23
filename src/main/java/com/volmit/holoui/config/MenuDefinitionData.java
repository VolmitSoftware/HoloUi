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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.util.Vector;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class MenuDefinitionData {
    private static final double MAX_DISTANCE = 6E7;

    private final Vector offset;
    private final boolean lockPosition, followPlayer;
    @Getter(AccessLevel.NONE)
    private final Double maxDistance;
    private final boolean closeOnDeath, closeOnTeleport;
    private final List<MenuComponentData> components;
    @Setter
    private volatile String id;

    public double getMaxDistance() {
        return maxDistance != null ? Math.min(Math.max(maxDistance, 0), MAX_DISTANCE) : MAX_DISTANCE;
    }
}
