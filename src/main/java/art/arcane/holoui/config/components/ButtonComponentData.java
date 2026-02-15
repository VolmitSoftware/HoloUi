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

import com.google.gson.annotations.SerializedName;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.action.MenuActionData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.enums.MenuComponentType;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.ButtonComponent;
import art.arcane.holoui.menu.components.MenuComponent;

import java.util.List;

public record ButtonComponentData(
        @SerializedName("highlightModifier")
        float highlightMod,
        List<MenuActionData> actions,
        @SerializedName("icon")
        MenuIconData iconData
) implements ComponentData {

    public MenuComponentType getType() {
        return MenuComponentType.BUTTON;
    }

    @Override
    public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
        return new ButtonComponent(session, data);
    }
}
