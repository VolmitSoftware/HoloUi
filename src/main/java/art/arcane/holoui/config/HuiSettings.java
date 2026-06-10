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
package art.arcane.holoui.config;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.util.common.settings.EntryType;
import art.arcane.holoui.util.common.settings.Settings;

import java.io.File;

public class HuiSettings extends Settings {
  public static final Entry<Boolean> DEBUG_HITBOX = new Entry<>(EntryType.BOOLEAN, false, b -> HoloUI.INSTANCE.getSessionManager().controlHitboxDebug(b));
  public static final Entry<Boolean> DEBUG_SPACING = new Entry<>(EntryType.BOOLEAN, false, b -> HoloUI.INSTANCE.getSessionManager().controlPositionDebug(b));
  public static final Entry<String> BUILDER_IP = new Entry<>(EntryType.STRING, "0.0.0.0", b -> {
  });
  public static final Entry<Integer> BUILDER_PORT = new Entry<>(EntryType.INTEGER, 8080, i -> {
  });
  public static final Entry<Boolean> PREVIEW_ENABLED = new Entry<>(EntryType.BOOLEAN, true, i -> {
  });
  public static final Entry<Boolean> PREVIEW_BY_PERMISSION = new Entry<>(EntryType.BOOLEAN, true, i -> {
  });
  public static final Entry<Double> PREVIEW_LOOK_DISTANCE = new Entry<>(EntryType.DOUBLE, 10.00D, i -> {
  });
  public static final Entry<Double> PREVIEW_SCALE = new Entry<>(EntryType.DOUBLE, 0.65D, i -> refreshVisuals());
  public static final Entry<Double> UI_SCALE = new Entry<>(EntryType.DOUBLE, 1.00D, i -> refreshVisuals());
  private static final double UI_SCALE_MIN = 0.25D;
  private static final double UI_SCALE_MAX = 4.00D;
  private static final double PREVIEW_SCALE_MIN = 0.25D;
  private static final double PREVIEW_SCALE_MAX = 4.00D;
  private static final double PREVIEW_DISTANCE_MIN = 1.00D;
  private static final double PREVIEW_DISTANCE_MAX = 24.00D;

  public HuiSettings(File configDir) {
    super(new File(configDir, "settings.json"));
  }

  public static float uiScale() {
    Double configured = UI_SCALE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 1.00F;
    double clamped = Math.max(UI_SCALE_MIN, Math.min(UI_SCALE_MAX, configured));
    return (float) clamped;
  }

  public static float previewScale() {
    Double configured = PREVIEW_SCALE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 0.65F;
    double clamped = Math.max(PREVIEW_SCALE_MIN, Math.min(PREVIEW_SCALE_MAX, configured));
    return (float) clamped;
  }

  public static double previewLookDistance() {
    Double configured = PREVIEW_LOOK_DISTANCE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 10.00D;
    return Math.max(PREVIEW_DISTANCE_MIN, Math.min(PREVIEW_DISTANCE_MAX, configured));
  }

  private static void refreshVisuals() {
    if (HoloUI.INSTANCE == null || HoloUI.INSTANCE.getSessionManager() == null)
      return;
    HoloUI.INSTANCE.getSessionManager().refreshVisuals();
  }

  @Override
  protected void registerFields() {
    registerField("debugHitbox", DEBUG_HITBOX);
    registerField("debugPosition", DEBUG_SPACING);
    registerField("builderIp", BUILDER_IP);
    registerField("builderPort", BUILDER_PORT);

    registerField("previewEnabled", PREVIEW_ENABLED);
    registerField("previewByPermission", PREVIEW_BY_PERMISSION);
    registerField("previewLookDistance", PREVIEW_LOOK_DISTANCE);
    registerField("previewScale", PREVIEW_SCALE);
    registerField("uiScale", UI_SCALE);
  }
}
