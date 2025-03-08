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
package com.volmit.holoui.utils.math;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class MathHelper {

    public static Location rotateAroundPoint(Location loc, Location center, float pitch, float yaw) {
        loc.subtract(center);
        Vector newPos = loc.toVector().rotateAroundX(Math.toRadians(pitch)).rotateAroundY(Math.toRadians(yaw));
        loc.setX(newPos.getX());
        loc.setY(newPos.getY());
        loc.setZ(newPos.getZ());
        return loc.add(center);
    }

    public static Vector unit(Vector a, Vector b) {
        return b.clone().subtract(a);
    }

    public static Vector interpolate(Vector start, Vector end, float delta) {
        return start.clone().add(end.clone().subtract(start).multiply(delta));
    }

    public static Vector getRotationFromDirection(Vector dir) {
        double _2PI = 2 * Math.PI;
        double x = dir.getX();
        double z = dir.getZ();
        Vector rot = new Vector();

        if (x == 0 && z == 0) {
            rot.setX(dir.getY() > 0 ? -90 : 90);
            return rot;
        }

        double theta = Math.atan2(-x, z);
        rot.setY(Math.toDegrees((theta + _2PI) % _2PI));

        double x2 = Math.pow(x, 2);
        double z2 = Math.pow(z, 2);
        double xz = Math.sqrt(x2 + z2);
        rot.setX(Math.toDegrees(Math.atan(-dir.getY() / xz)));
        return rot;
    }

    public static float getYawToLookAt(Vector origin, Vector target) {
        Vector v = target.clone().subtract(origin);
        return (float) Math.toDegrees(Math.atan2(v.getZ(), v.getX()));
    }
}
