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
import art.arcane.holoui.integration.ItemProviderRegistry;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.holoui.util.common.settings.EntryType;
import art.arcane.holoui.util.common.settings.Settings;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class HuiSettings extends Settings {
  public static final Entry<Boolean> DEBUG_HITBOX = new Entry<>(EntryType.BOOLEAN, false, b -> onSessionManager(m -> m.controlHitboxDebug(b)));
  public static final Entry<Boolean> DEBUG_SPACING = new Entry<>(EntryType.BOOLEAN, false, b -> onSessionManager(m -> m.controlPositionDebug(b)));
  public static final String BUILDER_URL_DEFAULT = "https://holoui.volmitsoftware.com";
  public static final Entry<String> BUILDER_URL = new Entry<>(EntryType.STRING, BUILDER_URL_DEFAULT, s -> {
  });
  public static final Entry<Boolean> PREVIEW_ENABLED = new Entry<>(EntryType.BOOLEAN, true, i -> {
  });
  public static final Entry<Double> PREVIEW_LOOK_DISTANCE = new Entry<>(EntryType.DOUBLE, 10.00D, i -> {
  });
  public static final Entry<Double> PREVIEW_SCALE = new Entry<>(EntryType.DOUBLE, 0.65D, i -> refreshVisuals());
  public static final Entry<Double> UI_SCALE = new Entry<>(EntryType.DOUBLE, 1.00D, i -> refreshVisuals());
  public static final Entry<Boolean> CUSTOM_ITEMS = new Entry<>(EntryType.BOOLEAN, true, i -> reloadItemProviders());
  public static final Entry<String> CUSTOM_ITEM_PROVIDERS = new Entry<>(EntryType.STRING, "", i -> reloadItemProviders());
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

  public static boolean previewEnabled() {
    Boolean configured = PREVIEW_ENABLED.value();
    return configured == null || configured;
  }

  public static String builderUrl() {
    return sanitizeBuilderUrl(BUILDER_URL.value());
  }

  /**
   * The url is handed straight to a MiniMessage {@code click:open_url} event, so anything that is not
   * a plain http(s) link, or that could break out of the quoted tag argument, falls back to the
   * shipped default rather than rendering a broken line.
   */
  public static String sanitizeBuilderUrl(String configured) {
    if (configured == null)
      return BUILDER_URL_DEFAULT;
    String trimmed = configured.trim();
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
      return BUILDER_URL_DEFAULT;
    for (int i = 0; i < trimmed.length(); i++) {
      char current = trimmed.charAt(i);
      if (current <= ' ' || current == '\'' || current == '"' || current == '<' || current == '>' || current == '\\')
        return BUILDER_URL_DEFAULT;
    }
    return trimmed;
  }

  public static boolean customItemsEnabled() {
    Boolean configured = CUSTOM_ITEMS.value();
    return configured == null || configured;
  }

  // EntryType has no list type, so the allowlist is a comma separated string, empty meaning every provider
  public static Set<String> customItemProviders() {
    String configured = CUSTOM_ITEM_PROVIDERS.value();
    if (configured == null || configured.isBlank())
      return Set.of();
    Set<String> allowed = new HashSet<>();
    for (String entry : configured.split(",")) {
      String normalized = entry.trim().toLowerCase(Locale.ROOT);
      if (!normalized.isEmpty())
        allowed.add(normalized);
    }
    return Set.copyOf(allowed);
  }

  private static void refreshVisuals() {
    onItemProviders(ItemProviderRegistry::invalidate);
    onSessionManager(MenuSessionManager::refreshVisuals);
  }

  private static void reloadItemProviders() {
    onItemProviders(ItemProviderRegistry::reload);
  }

  private static void onItemProviders(Consumer<ItemProviderRegistry> action) {
    HoloUI plugin = HoloUI.INSTANCE;
    ItemProviderRegistry itemProviders = plugin == null ? null : plugin.getItemProviders();
    if (itemProviders == null)
      return;
    action.accept(itemProviders);
  }

  private static void onSessionManager(Consumer<MenuSessionManager> action) {
    HoloUI plugin = HoloUI.INSTANCE;
    MenuSessionManager sessionManager = plugin == null ? null : plugin.getSessionManager();
    if (sessionManager == null)
      return;
    action.accept(sessionManager);
  }

  @Override
  protected void registerFields() {
    registerField("debugHitbox", DEBUG_HITBOX);
    registerField("debugPosition", DEBUG_SPACING);
    registerField("builderUrl", BUILDER_URL);

    registerField("previewEnabled", PREVIEW_ENABLED);
    registerField("previewLookDistance", PREVIEW_LOOK_DISTANCE);
    registerField("previewScale", PREVIEW_SCALE);
    registerField("uiScale", UI_SCALE);

    registerField("customItems", CUSTOM_ITEMS);
    registerField("customItemProviders", CUSTOM_ITEM_PROVIDERS);
  }
}
