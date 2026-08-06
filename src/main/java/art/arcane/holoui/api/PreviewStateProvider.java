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
package art.arcane.holoui.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Contributes extra variables to container preview documents. Every entry a provider returns is
 * exposed to preview expressions under {@code namespace() + "." + entryKey}, so a provider with
 * namespace {@code "adapt"} returning {@code {"level": 12}} publishes {@code adapt.level}.
 *
 * <p>Register with {@link PreviewStateProviders#register(PreviewStateProvider)}.
 */
public interface PreviewStateProvider {

  /**
   * Prefix for every variable this provider publishes. Must be non-blank and should be the owning
   * plugin's lowercase id; a blank namespace is skipped.
   */
  String namespace();

  /**
   * Samples the provider's state for one preview target. Called on the region thread that owns the
   * target, at most once per game tick per preview, so Bukkit access is safe but must be cheap.
   *
   * <p>Values are coerced to the expression runtime's types: any {@link Number} becomes a double,
   * {@link Boolean} and {@link String} pass through, and anything else is dropped. Return an empty
   * map (or {@code null}) when there is nothing to contribute. Implementations must not throw; a
   * failing provider is skipped for that sample and does not affect the rest of the preview.
   *
   * @param block the previewed block, or null when previewing an entity or a bare inventory
   * @param entity the previewed entity, or null when previewing a block or a bare inventory
   * @param player the viewer, or null when the preview has no viewer context
   */
  Map<String, Object> snapshot(Block block, Entity entity, Player player);
}
