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
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.icon.*;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.special.BlockMenuSession;
import art.arcane.holoui.util.common.math.CollisionPlane;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public abstract class MenuIcon<D extends MenuIconData> {

  protected static final float NAMETAG_SIZE = 1 / 16F * 3.5F;

  protected final MenuSession session;
  protected final D data;

  protected List<UUID> displayEntities;
  protected Location position;

  public MenuIcon(MenuSession session, Location loc, D data) throws MenuIconException {
    this.session = session;
    this.position = loc.clone();
    this.position.setYaw(0F);
    this.position.setPitch(0F);
    this.data = data;
  }

  @NonNull
  public static MenuIcon<?> createIcon(MenuSession session, Location loc, MenuIconData data, MenuComponent<?> component) {
    try {
      if (data instanceof ItemIconData d)
        return new ItemMenuIcon(session, loc, d);
      else if (data instanceof TextImageIconData d)
        return new TextImageMenuIcon(session, loc, d);
      else if (data instanceof TextIconData d)
        return new TextMenuIcon(session, loc, d);
      else if (data instanceof AnimatedImageData d)
        return new AnimatedTextImageMenuIcon(session, loc, d);
      return new TextImageMenuIcon(session, loc);
    } catch (MenuIconException e) {
      HoloUI.logExceptionStack(false, e, "An error occurred while creating a Menu Icon for the component \"%s\":", component.getId());
      HoloUI.log(Level.WARNING, "Falling back to missing icon.");
      try {
        return new TextImageMenuIcon(session, loc);
      } catch (MenuIconException ignored) {
        //noinspection ConstantConditions
        return null;
      }
    }
  }

  protected abstract List<UUID> createDisplayEntities(Location loc);

  public abstract CollisionPlane createBoundingBox();

  public void tick() {
  }

  protected float uiScale() {
    float scale = HuiSettings.uiScale();
    if (session instanceof BlockMenuSession) {
      if (this instanceof ItemMenuIcon)
        scale *= HuiSettings.previewIconScale();
      else
        scale *= HuiSettings.previewTextScale();
    }
    return scale;
  }

  protected byte billboardMode() {
    if (session instanceof BlockMenuSession)
      return 1;
    return 0;
  }

  protected byte textFlags() {
    if (session instanceof BlockMenuSession)
      return 1;
    return 0;
  }

  protected int textBackgroundColor() {
    if (session instanceof BlockMenuSession)
      return 0x55000000;
    return 0;
  }

  protected float scaledTagSize() {
    return NAMETAG_SIZE * uiScale();
  }

  public void spawn() {
    Location spawnLocation = position.clone().subtract(0, scaledTagSize(), 0);
    spawnLocation.setYaw(0F);
    spawnLocation.setPitch(0F);
    displayEntities = createDisplayEntities(spawnLocation);
    displayEntities.forEach(a -> DisplayEntityManager.spawn(a, session.getPlayer()));
  }

  public void remove() {
    displayEntities.forEach(DisplayEntityManager::delete);
    displayEntities.clear();
  }

  public void move(Vector offset) {
    if (displayEntities != null && !displayEntities.isEmpty())
      displayEntities.forEach(a -> DisplayEntityManager.move(a, offset));
    this.position.add(offset);
  }

  public void rotate(float yaw) {
    if (billboardMode() != 0) {
      return;
    }
    if (displayEntities != null && !displayEntities.isEmpty())
      displayEntities.forEach(a -> DisplayEntityManager.rotate(a, yaw));
  }

  public void teleport(Location loc) {
    Vector offset = loc.toVector().subtract(position.toVector());
    move(offset);
  }
}
