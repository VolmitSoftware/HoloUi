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
package art.arcane.holoui.config.action;

import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.enums.MenuActionType;
import art.arcane.holoui.enums.SoundSource;
import art.arcane.volmlib.util.bukkit.registry.RegistryUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public record SoundActionData(String sound, SoundSource source, Float volume,
                              Float pitch, HoloClickTrigger trigger) implements MenuActionData {

  private static final ConcurrentMap<String, Optional<Sound>> RESOLVED_SOUNDS = new ConcurrentHashMap<>();

  public MenuActionType getType() {
    return MenuActionType.SOUND;
  }

  public SoundSource sourceOrDefault() {
    return source == null ? SoundSource.MASTER : source;
  }

  public float volumeOrDefault() {
    return volume == null ? 1F : volume;
  }

  public float pitchOrDefault() {
    return pitch == null ? 1F : pitch;
  }

  public Sound resolveSound() {
    if (sound == null || sound.isBlank())
      return null;

    return RESOLVED_SOUNDS.computeIfAbsent(sound, SoundActionData::findSound).orElse(null);
  }

  private static Optional<Sound> findSound(String sound) {
    try {
      NamespacedKey key = NamespacedKey.fromString(sound);
      return Optional.ofNullable(key == null ? null : RegistryUtil.find(Sound.class, key));
    } catch (RuntimeException | LinkageError ex) {
      return Optional.empty();
    }
  }
}
