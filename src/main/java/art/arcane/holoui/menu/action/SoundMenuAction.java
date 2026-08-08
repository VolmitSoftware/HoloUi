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
package art.arcane.holoui.menu.action;

import art.arcane.holoui.config.action.SoundActionData;
import art.arcane.holoui.menu.MenuSession;
import org.bukkit.Sound;

public class SoundMenuAction extends MenuAction<SoundActionData> {

  private final Sound sound;

  public SoundMenuAction(SoundActionData data) {
    super(data);
    this.sound = data.resolveSound();
  }

  public boolean hasSound() {
    return sound != null;
  }

  @Override
  public void execute(MenuSession session) {
    if (sound == null)
      return;
    session.getPlayer().playSound(session.getPlayer().getLocation(), sound, data.sourceOrDefault().getCategory(), data.volumeOrDefault(), data.pitchOrDefault());
  }
}
