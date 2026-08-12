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
package art.arcane.holoui.config.icon;

public record IconDisplayStyle(
    IconBillboard billboard,
    Boolean shadow,
    Boolean seeThrough,
    IconTextAlignment textAlignment,
    IconArgbColor backgroundArgb,
    Integer textOpacity,
    Integer lineWidth,
    Integer blockLight,
    Integer skyLight,
    Float viewRange,
    Float shadowRadius,
    Float shadowStrength,
    Float cullingWidth,
    Float cullingHeight,
    IconArgbColor glowColor,
    Float scaleX,
    Float scaleY,
    Float scaleZ
) {
  private static final IconDisplayStyle DEFAULTS = new IconDisplayStyle(
      IconBillboard.FIXED,
      false,
      false,
      IconTextAlignment.CENTER,
      IconArgbColor.TRANSPARENT,
      255,
      2000,
      null,
      null,
      1F,
      0F,
      0F,
      0F,
      0F,
      null,
      1F,
      1F,
      1F
  );

  public IconDisplayStyle {
    billboard = billboard == null ? IconBillboard.FIXED : billboard;
    shadow = shadow == null ? false : shadow;
    seeThrough = seeThrough == null ? false : seeThrough;
    textAlignment = textAlignment == null ? IconTextAlignment.CENTER : textAlignment;
    backgroundArgb = backgroundArgb == null ? IconArgbColor.TRANSPARENT : backgroundArgb;
    textOpacity = textOpacity == null ? 255 : requireRange(textOpacity, 0, 255, "textOpacity");
    lineWidth = lineWidth == null ? 2000 : requireRange(lineWidth, 1, 16384, "lineWidth");
    if ((blockLight == null) != (skyLight == null)) {
      throw new IllegalArgumentException("blockLight and skyLight must be supplied together");
    }
    if (blockLight != null) {
      blockLight = requireRange(blockLight, 0, 15, "blockLight");
      skyLight = requireRange(skyLight, 0, 15, "skyLight");
    }
    viewRange = requireFiniteRange(viewRange == null ? 1F : viewRange, 0.01F, 64F, "viewRange");
    shadowRadius = requireFiniteRange(shadowRadius == null ? 0F : shadowRadius, 0F, 64F, "shadowRadius");
    shadowStrength = requireFiniteRange(shadowStrength == null ? 0F : shadowStrength, 0F, 1F, "shadowStrength");
    cullingWidth = requireFiniteRange(cullingWidth == null ? 0F : cullingWidth, 0F, 4096F, "cullingWidth");
    cullingHeight = requireFiniteRange(cullingHeight == null ? 0F : cullingHeight, 0F, 4096F, "cullingHeight");
    scaleX = requireFiniteRange(scaleX == null ? 1F : scaleX, 0.01F, 64F, "scaleX");
    scaleY = requireFiniteRange(scaleY == null ? 1F : scaleY, 0.01F, 64F, "scaleY");
    scaleZ = requireFiniteRange(scaleZ == null ? 1F : scaleZ, 0.01F, 64F, "scaleZ");
  }

  public static IconDisplayStyle defaults() {
    return DEFAULTS;
  }

  public static IconDisplayStyle resolve(IconDisplayStyle style) {
    return style == null ? DEFAULTS : style;
  }

  public byte textFlags() {
    int flags = textAlignment.textFlag();
    if (shadow) {
      flags |= 0x01;
    }
    if (seeThrough) {
      flags |= 0x02;
    }
    return (byte) flags;
  }

  public int packedBrightness() {
    if (blockLight == null) {
      return -1;
    }
    return (blockLight << 4) | (skyLight << 20);
  }

  public int glowColorOverride() {
    return glowColor == null ? -1 : glowColor.argb();
  }

  public byte entityFlags() {
    return glowColor == null ? (byte) 0 : (byte) 0x40;
  }

  private static int requireRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
    return value;
  }

  private static float requireFiniteRange(float value, float minimum, float maximum, String name) {
    if (!Float.isFinite(value) || value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
    }
    return value;
  }
}
