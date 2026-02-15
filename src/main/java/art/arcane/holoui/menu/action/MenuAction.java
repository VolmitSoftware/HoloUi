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

import art.arcane.holoui.config.action.CommandActionData;
import art.arcane.holoui.config.action.MenuActionData;
import art.arcane.holoui.config.action.SoundActionData;
import art.arcane.holoui.menu.MenuSession;

public abstract class MenuAction<E extends MenuActionData> {

    protected final E data;

    public MenuAction(E data) {
        this.data = data;
    }

    public static MenuAction<?> get(MenuActionData data) {
        if (data instanceof CommandActionData d)
            return new CommandMenuAction(d);
        else if (data instanceof SoundActionData d)
            return new SoundMenuAction(d);
        else
            return null;
    }

    public abstract void execute(MenuSession session);
}
