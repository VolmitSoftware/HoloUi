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
package art.arcane.holoui.menu.icon;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.Placeholders;
import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TextMenuIcon extends MenuIcon<TextIconData> {

  private final List<Component> components;
  private final int refreshInterval;
  private String sourceText;
  private boolean dynamicSource;
  private boolean refreshFailureLogged;
  private int refreshCountdown;

  public TextMenuIcon(MenuSession session, Location loc, TextIconData data) throws MenuIconException {
    super(session, loc, data);
    sourceText = data.text();
    dynamicSource = containsPlaceholderToken(sourceText);
    components = render(session, sourceText);
    refreshInterval = data.resolvedRefreshTicks();
    refreshCountdown = refreshInterval;
  }

  public static List<Component> render(MenuSession session, String text) {
    return Arrays.stream((text == null ? "" : text).split("\n"))
        .map(s -> TextUtils.parse(Placeholders.setPlaceholders(session.getPlayer(), s)))
        .collect(Collectors.toList());
  }

  @Override
  protected List<UUID> createDisplayEntities(Location loc) {
    List<UUID> uuids = Lists.newArrayList();
    Location lineLocation = session.getTransform().localPosition(
        loc,
        new Vector(0F, ((components.size() - 1) / 2F * localLineHeight()) - localLineHeight(), 0F)
    );
    components.forEach(c -> {
      uuids.add(DisplayEntityManager.add(textDisplay(c, lineLocation)));
      lineLocation.add(session.getTransform().localVector(new Vector(0F, -localLineHeight(), 0F)));
    });
    return uuids;
  }

  @Override
  public CollisionPlane createBoundingBox(Location anchor) {
    float lineHeight = scaledLineHeight();
    float characterWidth = scaledCharacterWidth();
    float width = 0;
    for (Component component : components)
      width = Math.max(width, TextUtils.content(component).length() * characterWidth / 2F);
    return session.getTransform().createPlane(textBoundingBoxCenter(anchor), width, components.size() * lineHeight);
  }

  @Override
  public void tick() {
    if (refreshInterval == 0 || !dynamicSource) {
      return;
    }
    refreshCountdown--;
    if (refreshCountdown > 0) {
      return;
    }
    refreshCountdown = refreshInterval;
    try {
      updateText(sourceText);
      refreshFailureLogged = false;
    } catch (RuntimeException failure) {
      if (!refreshFailureLogged) {
        refreshFailureLogged = true;
        HoloUI.logExceptionStack(false, failure,
            "Failed to refresh text placeholders in menu %s for %s; retaining the previous text.",
            session.getId(), session.getPlayer().getName());
      }
    }
  }

  public void updateName(int index, Component c) {
    if (index >= components.size())
      return;
    components.set(index, c);
    DisplayEntityManager.changeName(this.displayEntities.get(index), c);
  }

  public boolean updateText(String text) {
    sourceText = text;
    dynamicSource = containsPlaceholderToken(sourceText);
    if (displayEntities == null || displayEntities.size() != components.size())
      return false;

    List<Component> replacement = render(session, text);
    if (replacement.equals(components)) {
      return true;
    }

    if (replacement.size() != components.size()) {
      remove();
      components.clear();
      components.addAll(replacement);
      spawn();
      markGeometryChanged();
      return true;
    }

    for (int index = 0; index < replacement.size(); index++) {
      if (replacement.get(index).equals(components.get(index)))
        continue;
      updateName(index, replacement.get(index));
    }

    markGeometryChanged();
    return true;
  }

  private static boolean containsPlaceholderToken(String text) {
    if (text == null) {
      return false;
    }
    int opening = text.indexOf('%');
    return opening >= 0 && text.indexOf('%', opening + 1) > opening + 1;
  }
}
