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
import art.arcane.holoui.config.icon.AnimatedImageData;
import art.arcane.holoui.config.icon.CustomItemIconData;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.config.icon.ItemStackIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.config.icon.TextImageIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.integration.ItemProviderRegistry;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.util.common.math.CollisionPlane;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public abstract class MenuIcon<D extends MenuIconData> {

  protected static final float NAMETAG_SIZE = 1 / 16F * 3.5F;
  private static final float TEXT_DISPLAY_BASELINE = 4.5F / 40F;

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
      else if (data instanceof CustomItemIconData d)
        return new ItemMenuIcon(session, loc, d, resolveCustomItem(d));
      else if (data instanceof ItemStackIconData d)
        return new ItemMenuIcon(session, loc, d);
      else if (data instanceof TextImageIconData d)
        return new TextImageMenuIcon(session, loc, d);
      else if (data instanceof TextIconData d)
        return new TextMenuIcon(session, loc, d);
      else if (data instanceof AnimatedImageData d)
        return new AnimatedTextImageMenuIcon(session, loc, d);
      return new TextImageMenuIcon(session, loc);
    } catch (MenuIconException | RuntimeException e) {
      HoloUI.logExceptionStack(false, e, "An error occurred while creating a Menu Icon for the component \"%s\":", component.getId());
      HoloUI.log(Level.WARNING, "Falling back to missing icon.");
      try {
        return new TextImageMenuIcon(session, loc);
      } catch (MenuIconException | RuntimeException fallbackFailure) {
        IllegalStateException failure = new IllegalStateException("Failed to construct the missing-icon fallback", fallbackFailure);
        failure.addSuppressed(e);
        throw failure;
      }
    }
  }

  private static ItemStack resolveCustomItem(CustomItemIconData data) throws MenuIconException {
    HoloUI plugin = HoloUI.INSTANCE;
    ItemProviderRegistry registry = plugin == null ? null : plugin.getItemProviders();
    ItemStack resolved = registry == null ? null : registry.resolve(data.provider(), data.item());
    if (resolved == null)
      throw new MenuIconException("Unable to resolve custom item \"%s\" from provider \"%s\"",
          data.item(), data.provider() == null || data.provider().isBlank() ? ItemProviderRegistry.AUTO_PROVIDER : data.provider());
    resolved.setAmount(data.count() > 0 ? data.count() : 1);
    return resolved;
  }

  protected abstract List<UUID> createDisplayEntities(Location loc);

  public abstract CollisionPlane createBoundingBox(Location anchor);

  public void tick() {
  }

  protected float uiScale() {
    return HuiSettings.uiScale();
  }

  protected byte billboardMode() {
    return 0;
  }

  protected byte textFlags() {
    return 0;
  }

  protected int textBackgroundColor() {
    return 0;
  }

  protected float scaledTagSize() {
    return NAMETAG_SIZE * uiScale();
  }

  protected Vector textBoundingBoxCenter(Location anchor) {
    return textBoundingBoxCenter(anchor, uiScale());
  }

  static Vector textBoundingBoxCenter(Location anchor, float scale) {
    float offset = ((2F * NAMETAG_SIZE) - TEXT_DISPLAY_BASELINE) * scale;
    return anchor.toVector().subtract(new Vector(0F, offset, 0F));
  }

  public void spawn() {
    Location spawnLocation = position.clone().subtract(0, scaledTagSize(), 0);
    spawnLocation.setYaw(0F);
    spawnLocation.setPitch(0F);
    displayEntities = createDisplayEntities(spawnLocation);
    displayEntities.forEach(a -> DisplayEntityManager.spawn(a, session.getPlayer()));
  }

  public void remove() {
    if (displayEntities == null) {
      return;
    }
    displayEntities.forEach(uuid -> DisplayEntityManager.delete(uuid, session.getPlayer()));
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
