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
package com.volmit.holoui.menu.special;

import com.volmit.holoui.config.HuiSettings;
import com.volmit.holoui.config.MenuDefinitionData;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.utils.math.MathHelper;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BlockMenuSession extends MenuSession {

    private static final float MIN_DISTANCE = 2.75F;
    private static final float MAX_DISTANCE = 10.75F;

    private final Block block;

    public BlockMenuSession(MenuDefinitionData data, Player p, Block b) {
        super(data, p);
        this.block = b;
    }

    // TODO configurable max distance
    public boolean shouldRender(Block lookingAt) {
        if (!HuiSettings.PREVIEW_ENABLED.value()) return false;
        double distance = getPlayer().getLocation().distance(centerPoint);
        /*if(distance <= MIN_DISTANCE || distance >= MAX_DISTANCE)
            return false;*/
        return lookingAt.equals(this.block);
    }

    public boolean hasPermission() {
        return !HuiSettings.PREVIEW_BY_PERMISSION.value() || getPlayer().hasPermission("holoui.preview." + block.getType().getKey().getKey());
    }

    @Override
    public void rotate(float yaw) {
        super.rotate(yaw);
        this.initialY = yaw;
    }

    @Override
    public void move(Location loc, boolean byPlayer) {
        if (!HuiSettings.PREVIEW_FOLLOW_PLAYER.value())
            this.centerPoint = block.getLocation().add(getOffset()).clone().subtract(-.5, -.5, -.5);
        else
            this.centerPoint = loc.add(getOffset());
        rotateCenter();
        adjustRotation(byPlayer);
    }

    public void open() {
        this.initialY = -(float) MathHelper.getRotationFromDirection(getPlayer().getEyeLocation().getDirection()).getY();
        getComponents().forEach(c -> c.open(false));
    }
}
