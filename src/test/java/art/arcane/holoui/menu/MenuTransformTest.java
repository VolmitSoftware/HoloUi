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
package art.arcane.holoui.menu;

import art.arcane.holoui.util.common.math.CollisionPlane;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MenuTransformTest {

  @Test
  public void renderBlockOrbitAndHitboxShareOneYawAndScale() {
    MenuTransform transform = new MenuTransform(
        new Location(null, 10D, 64D, 20D),
        new Vector(0D, 1D, 2D),
        90F,
        0F,
        0F,
        2F
    );
    Location component = transform.componentPosition(new Vector(1D, 0.5D, 0.25D));
    Location block = transform.localPosition(component, new Vector(0D, -0.95D, 0.3D));
    CollisionPlane plane = transform.createPlane(component.toVector(), 2F, 1F);
    Vector expectedNormal = transform.localVector(new Vector(0D, 0D, -1D)).normalize();
    Vector expectedBlockOffset = transform.localVector(new Vector(0D, -0.95D, 0.3D));

    assertEquals(270F, component.getYaw(), 0F);
    assertVector(expectedNormal, plane.getNormal());
    assertVector(expectedBlockOffset, block.toVector().subtract(component.toVector()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonPositiveScale() {
    new MenuTransform(new Location(null, 0D, 0D, 0D), new Vector(), 0F, 0F, 0F, 0F);
  }

  @Test
  public void pitchAndRollRotateLayoutAndHitboxTogether() {
    MenuTransform transform = new MenuTransform(
        new Location(null, 4D, 8D, 12D),
        new Vector(),
        35F,
        25F,
        -15F,
        1.5F
    );
    Vector center = transform.componentPosition(new Vector(0.5D, 0.75D, 0.25D)).toVector();
    CollisionPlane plane = transform.createPlane(center, 2F, 1F);
    Vector expectedNormal = transform.localVector(new Vector(0D, 0D, -1D)).normalize();
    Vector expectedUp = transform.localVector(new Vector(0D, 1D, 0D)).normalize();

    assertVector(expectedNormal, plane.getNormal());
    assertVector(expectedUp, plane.getUp());
    assertEquals(25F, transform.pitch(), 0F);
    assertEquals(-15F, transform.roll(), 0F);
  }

  private static void assertVector(Vector expected, Vector actual) {
    assertEquals(expected.getX(), actual.getX(), 0.000001D);
    assertEquals(expected.getY(), actual.getY(), 0.000001D);
    assertEquals(expected.getZ(), actual.getZ(), 0.000001D);
  }
}
