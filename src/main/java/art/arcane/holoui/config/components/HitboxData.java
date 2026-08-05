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
package art.arcane.holoui.config.components;

import org.bukkit.util.Vector;

public record HitboxData(Float width, Float height, Vector offset, HitboxAnchor anchor) {

  public HitboxData {
    if ((width == null) != (height == null))
      throw new IllegalArgumentException("Button hitbox width and height must be supplied together.");
    if (width != null && (!Float.isFinite(width) || width <= 0F))
      throw new IllegalArgumentException("Button hitbox width must be finite and greater than zero.");
    if (height != null && (!Float.isFinite(height) || height <= 0F))
      throw new IllegalArgumentException("Button hitbox height must be finite and greater than zero.");
    if (offset != null && (!Double.isFinite(offset.getX()) || !Double.isFinite(offset.getY()) || !Double.isFinite(offset.getZ())))
      throw new IllegalArgumentException("Button hitbox offset must contain only finite values.");
  }

  public boolean hasCustomSize() {
    return width != null;
  }

  public HitboxAnchor anchorOrDefault() {
    return anchor == null ? HitboxAnchor.BUTTON : anchor;
  }

  public float scaledWidth(float scale) {
    return width * scale;
  }

  public float scaledHeight(float scale) {
    return height * scale;
  }

  public Vector scaledOffset(float scale) {
    return offset == null ? new Vector() : offset.clone().multiply(scale);
  }
}
