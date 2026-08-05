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
package art.arcane.holoui.menu.icon;

import art.arcane.holoui.util.common.math.CollisionPlane;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MenuIconGeometryTest {

  @Test
  public void textPlaneTracksTheVisibleGlyphCenterAtEveryScale() {
    Location anchor = new Location(null, 1D, 2D, 3D);

    Vector normalScale = MenuIcon.textBoundingBoxCenter(anchor, 1F);
    Vector largeScale = MenuIcon.textBoundingBoxCenter(anchor, 2.5F);

    assertEquals(1D, normalScale.getX(), 0D);
    assertEquals(1.675D, normalScale.getY(), 0.000001D);
    assertEquals(3D, normalScale.getZ(), 0D);
    assertEquals(1.1875D, largeScale.getY(), 0.000001D);
  }

  @Test
  public void lookingAtVisibleTextHitsWithoutAimingAtTheGapAboveIt() {
    Location anchor = new Location(null, 0D, 0D, 3D);
    Vector visibleCenter = MenuIcon.textBoundingBoxCenter(anchor, 1F);
    CollisionPlane plane = new CollisionPlane(visibleCenter, 1F, MenuIcon.NAMETAG_SIZE);
    Vector origin = new Vector(0D, 0D, 0D);

    assertTrue(plane.isLookingAt(origin, visibleCenter.clone().subtract(origin).normalize()));
    assertFalse(plane.isLookingAt(origin, anchor.toVector().subtract(origin).normalize()));
  }
}
