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
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;

public class HuiSettings extends Settings {
  public static final Entry<Boolean> DEBUG_HITBOX = new Entry<>(EntryType.BOOLEAN, false, b -> HoloUI.INSTANCE.getSessionManager().controlHitboxDebug(b));
  public static final Entry<Boolean> DEBUG_SPACING = new Entry<>(EntryType.BOOLEAN, false, b -> HoloUI.INSTANCE.getSessionManager().controlPositionDebug(b));
  public static final Entry<String> BUILDER_IP = new Entry<>(EntryType.STRING, "0.0.0.0", b -> {
  });
  public static final Entry<Integer> BUILDER_PORT = new Entry<>(EntryType.INTEGER, 8080, i -> {
  });
  public static final Entry<Boolean> PREVIEW_FOLLOW_PLAYER = new Entry<>(EntryType.BOOLEAN, false, i -> {
  });
  public static final Entry<Boolean> PREVIEW_ENABLED = new Entry<>(EntryType.BOOLEAN, true, i -> {
  });
  public static final Entry<Boolean> PREVIEW_BY_PERMISSION = new Entry<>(EntryType.BOOLEAN, true, i -> {
  });
  public static final Entry<Double> PREVIEW_LOOK_DISTANCE = new Entry<>(EntryType.DOUBLE, 10.00D, i -> {
  });
  public static final Entry<Double> PREVIEW_ANCHOR_HEIGHT = new Entry<>(EntryType.DOUBLE, 1.20D, i -> refreshVisuals());
  public static final Entry<Double> PREVIEW_ANCHOR_PUSH = new Entry<>(EntryType.DOUBLE, 0.42D, i -> refreshVisuals());
  public static final Entry<Double> PREVIEW_LAYOUT_SCALE = new Entry<>(EntryType.DOUBLE, 0.38D, i -> refreshVisuals());
  public static final Entry<Double> PREVIEW_PANEL_SCALE = new Entry<>(EntryType.DOUBLE, 1.00D, i -> refreshVisuals());
  public static final Entry<Double> PREVIEW_ICON_SCALE = new Entry<>(EntryType.DOUBLE, 0.22D, i -> refreshVisuals());
  public static final Entry<Double> PREVIEW_TEXT_SCALE = new Entry<>(EntryType.DOUBLE, 0.46D, i -> refreshVisuals());
  public static final Entry<Boolean> PREVIEW_SHOW_EMPTY_SLOTS = new Entry<>(EntryType.BOOLEAN, false, i -> refreshVisuals());
  public static final Entry<String> PREVIEW_EMPTY_SLOT_ITEM = new Entry<>(EntryType.STRING, "LIGHT_GRAY_STAINED_GLASS_PANE", i -> refreshVisuals());
  public static final Entry<Double> UI_SCALE = new Entry<>(EntryType.DOUBLE, 1.00D, i -> refreshVisuals());
  private static final double UI_SCALE_MIN = 0.25D;
  private static final double UI_SCALE_MAX = 4.00D;
  private static final double PREVIEW_SCALE_MIN = 0.10D;
  private static final double PREVIEW_SCALE_MAX = 2.00D;
  private static final double PREVIEW_DISTANCE_MIN = 1.00D;
  private static final double PREVIEW_DISTANCE_MAX = 24.00D;
  private static final double PREVIEW_HEIGHT_MIN = -2.00D;
  private static final double PREVIEW_HEIGHT_MAX = 4.00D;
  private static final double PREVIEW_PUSH_MIN = -2.00D;
  private static final double PREVIEW_PUSH_MAX = 4.00D;

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

  public static float previewLayoutScale() {
    Double configured = PREVIEW_LAYOUT_SCALE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 0.38F;
    double clamped = Math.max(PREVIEW_SCALE_MIN, Math.min(PREVIEW_SCALE_MAX, configured));
    return (float) clamped;
  }

  public static float previewIconScale() {
    Double configured = PREVIEW_ICON_SCALE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 0.22F;
    double clamped = Math.max(PREVIEW_SCALE_MIN, Math.min(PREVIEW_SCALE_MAX, configured));
    return (float) clamped;
  }

  public static float previewPanelScale() {
    Double configured = PREVIEW_PANEL_SCALE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 1.00F;
    double clamped = Math.max(PREVIEW_SCALE_MIN, Math.min(PREVIEW_SCALE_MAX, configured));
    return (float) clamped;
  }

  public static float previewTextScale() {
    Double configured = PREVIEW_TEXT_SCALE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 0.46F;
    double clamped = Math.max(PREVIEW_SCALE_MIN, Math.min(PREVIEW_SCALE_MAX, configured));
    return (float) clamped;
  }

  public static double previewLookDistance() {
    Double configured = PREVIEW_LOOK_DISTANCE.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 10.00D;
    return Math.max(PREVIEW_DISTANCE_MIN, Math.min(PREVIEW_DISTANCE_MAX, configured));
  }

  public static double previewAnchorHeight() {
    Double configured = PREVIEW_ANCHOR_HEIGHT.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 1.20D;
    return Math.max(PREVIEW_HEIGHT_MIN, Math.min(PREVIEW_HEIGHT_MAX, configured));
  }

  public static double previewAnchorPush() {
    Double configured = PREVIEW_ANCHOR_PUSH.value();
    if (configured == null || configured.isNaN() || configured.isInfinite())
      return 0.42D;
    return Math.max(PREVIEW_PUSH_MIN, Math.min(PREVIEW_PUSH_MAX, configured));
  }

  public static boolean showPreviewEmptySlots() {
    Boolean configured = PREVIEW_SHOW_EMPTY_SLOTS.value();
    return configured != null && configured;
  }

  public static ItemStack previewEmptySlotItem() {
    String configured = PREVIEW_EMPTY_SLOT_ITEM.value();
    Material material = null;
    if (configured != null && !configured.isBlank()) {
      material = Material.matchMaterial(configured.trim(), true);
    }
    if (material == null || material == Material.AIR) {
      material = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    }
    return new ItemStack(material);
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

    registerField("previewFollowPlayer", PREVIEW_FOLLOW_PLAYER);
    registerField("previewEnabled", PREVIEW_ENABLED);
    registerField("previewByPermission", PREVIEW_BY_PERMISSION);
    registerField("previewLookDistance", PREVIEW_LOOK_DISTANCE);
    registerField("previewAnchorHeight", PREVIEW_ANCHOR_HEIGHT);
    registerField("previewAnchorPush", PREVIEW_ANCHOR_PUSH);
    registerField("previewLayoutScale", PREVIEW_LAYOUT_SCALE);
    registerField("previewPanelScale", PREVIEW_PANEL_SCALE);
    registerField("previewIconScale", PREVIEW_ICON_SCALE);
    registerField("previewTextScale", PREVIEW_TEXT_SCALE);
    registerField("previewShowEmptySlots", PREVIEW_SHOW_EMPTY_SLOTS);
    registerField("previewEmptySlotItem", PREVIEW_EMPTY_SLOT_ITEM);
    registerField("uiScale", UI_SCALE);
  }
}
