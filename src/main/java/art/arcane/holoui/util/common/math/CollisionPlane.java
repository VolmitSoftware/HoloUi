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
package art.arcane.holoui.util.common.math;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.OptionalDouble;

@Getter
public class CollisionPlane {

  private static final Vector UP = new Vector(0, 1, 0);
  private static final Vector RIGHT = new Vector(1, 0, 0);

  private Vector up, right, center, normal;
  private float width, height, pitch, yaw, roll;

  public CollisionPlane(Vector center, float width, float height) {
    this.center = center;
    this.width = width;
    this.height = height;
    this.up = UP.clone();
    this.right = RIGHT.clone();
    calcNormal();
  }

  public boolean isLookingAt(Vector origin, Vector direction) {
    return intersectionDistance(origin, direction).isPresent();
  }

  public OptionalDouble intersectionDistance(Vector origin, Vector direction) {
    Vector offset = center.clone().subtract(origin);
    double proj = normal.dot(direction);
    if (Math.abs(proj) < 1.0E-9D) {
      return OptionalDouble.empty();
    }
    double distance = normal.dot(offset) / proj;
    if (distance < 0.0D) {
      return OptionalDouble.empty();
    }
    Vector intersect = origin.clone().add(direction.clone().multiply(distance)).subtract(center);
    float distX = (float) Math.abs(right.dot(intersect));
    float distY = (float) Math.abs(up.dot(intersect));
    return distX < width / 2F && distY < height / 2F
        ? OptionalDouble.of(distance)
        : OptionalDouble.empty();
  }

  public void rotate(float pitch, float yaw, float roll) {
    if (pitch != this.pitch || yaw != this.yaw || roll != this.roll) {
      this.pitch = pitch;
      this.yaw = yaw;
      this.roll = roll;
      this.up = rotate(UP.clone(), pitch, yaw, roll);
      this.right = rotate(RIGHT.clone(), pitch, yaw, roll);
      calcNormal();
    }
  }

  public void orient(Vector nextUp, Vector nextRight) {
    Vector normalizedRight = nextRight.clone();
    if (normalizedRight.lengthSquared() < 1.0E-12D) {
      return;
    }
    normalizedRight.normalize();
    Vector normalizedUp = nextUp.clone().subtract(
        normalizedRight.clone().multiply(nextUp.dot(normalizedRight))
    );
    if (normalizedUp.lengthSquared() < 1.0E-12D) {
      return;
    }
    this.right = normalizedRight;
    this.up = normalizedUp.normalize();
    calcNormal();
  }

  public void move(Location loc) {
    this.center = loc.toVector();
  }

  public void translate(double x, double y, double z) {
    translate(new Vector(x, y, z));
  }

  public void translate(Vector vec) {
    this.center.add(vec);
  }

  public void resize(float width, float height) {
    this.width = width;
    this.height = height;
  }

  private void calcNormal() {
    this.normal = up.clone().crossProduct(right).normalize();
  }

  private Vector rotate(Vector vector, float pitch, float yaw, float roll) {
    return vector
        .rotateAroundZ(Math.toRadians(roll))
        .rotateAroundX(Math.toRadians(pitch))
        .rotateAroundY(Math.toRadians(yaw));
  }
}
