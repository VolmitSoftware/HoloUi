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

import java.util.regex.Pattern;

public record ConnectActionData(String server, HoloClickTrigger trigger) implements MenuActionData {
  private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

  @Override
  public MenuActionType getType() {
    return MenuActionType.CONNECT;
  }

  public boolean hasValidServer() {
    return server != null && SERVER_NAME.matcher(server).matches();
  }
}
