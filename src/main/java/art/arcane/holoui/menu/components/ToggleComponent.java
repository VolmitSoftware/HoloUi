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
import art.arcane.holoui.config.components.ToggleComponentData;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.action.MenuAction;
import art.arcane.holoui.menu.icon.MenuIcon;
import art.arcane.volmlib.util.bukkit.Placeholders;
import com.google.common.collect.Lists;
import org.bukkit.Location;

import java.util.List;

public class ToggleComponent extends ClickableComponent<ToggleComponentData> {

  private final String condition, expected;
  private final MenuIcon<?> trueIcon, falseIcon;
  private final List<MenuAction<?>> trueActions, falseActions;

  private boolean state;

  public ToggleComponent(MenuSession session, MenuComponentData data) {
    super(session, data, ((ToggleComponentData) data.data()).highlightMod());
    this.condition = this.data.condition();
    this.expected = this.data.expectedValue();
    this.trueIcon = MenuIcon.createIcon(session, location, this.data.trueIcon(), this);
    this.falseIcon = MenuIcon.createIcon(session, location, this.data.falseIcon(), this);
    this.trueActions = Lists.newArrayList();
    this.data.trueActions().forEach(a -> trueActions.add(MenuAction.get(a)));
    this.falseActions = Lists.newArrayList();
    this.data.falseActions().forEach(a -> falseActions.add(MenuAction.get(a)));

    state = isValid();
  }

  @Override
  public void onClick() {
    if (state) {
      falseActions.forEach(a -> a.execute(session));
      changeIcon(falseIcon);
      state = false;
    } else {
      trueActions.forEach(a -> a.execute(session));
      changeIcon(trueIcon);
      state = true;
    }
  }

  @Override
  protected MenuIcon<?> createIcon() {
    falseIcon.teleport(location);
    trueIcon.teleport(location);
    return state ? trueIcon : falseIcon;
  }

  @Override
  public void move(Location loc) {
    super.move(loc);
    falseIcon.teleport(location);
    trueIcon.teleport(location);
  }

  private void changeIcon(MenuIcon<?> icon) {
    this.currentIcon.remove();
    this.currentIcon = icon;
    this.currentIcon.teleport(location.clone());
    this.plane = this.currentIcon.createBoundingBox();
    this.currentIcon.spawn();
  }

  private boolean isValid() {
    return Placeholders.setPlaceholders(session.getPlayer(), condition).equalsIgnoreCase(expected);
  }
}
