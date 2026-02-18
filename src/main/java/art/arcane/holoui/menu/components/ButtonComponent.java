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
package art.arcane.holoui.menu.components;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ButtonComponentData;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.action.MenuAction;
import art.arcane.holoui.menu.icon.MenuIcon;
import com.google.common.collect.Lists;

import java.util.List;

public class ButtonComponent extends ClickableComponent<ButtonComponentData> {

  private final List<MenuAction<?>> actions;

  public ButtonComponent(MenuSession session, MenuComponentData data) {
    super(session, data, ((ButtonComponentData) data.data()).highlightMod());
    this.actions = Lists.newArrayList();
    this.data.actions().forEach(a -> actions.add(MenuAction.get(a)));
  }

  @Override
  public MenuIcon<?> createIcon() {
    return MenuIcon.createIcon(session, location, data.iconData(), this);
  }

  @Override
  public void onClick() {
    actions.forEach(a -> a.execute(session));
  }
}
