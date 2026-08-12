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

import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.internal.ApiMenuTranslator;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.icon.MenuIcon;
import art.arcane.holoui.menu.icon.TextMenuIcon;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public abstract class MenuComponent<T extends ComponentData> {

  protected final MenuSession session;
  protected final Vector offset;
  protected final T data;

  @Getter
  protected final String id;

  @Getter
  protected Location location;
  protected MenuIcon<?> currentIcon;
  private long observedGeometryRevision;

  @Getter
  protected boolean open = false;

  @SuppressWarnings("unchecked")
  public MenuComponent(MenuSession session, MenuComponentData data) {
    this.session = session;
    this.id = data.id();
    this.offset = data.offset().clone();
    this.data = (T) data.data();

    this.location = session.getTransform().componentPosition(offset);
  }

  public void tick() {
    if (!open) return;
    if (currentIcon != null) {
      currentIcon.tick();
      refreshDynamicGeometry();
    }
    onTick();
  }

  protected abstract void onTick();

  protected abstract MenuIcon<?> createIcon();

  protected abstract void onOpen();

  protected abstract void onClose();

  public void open() {
    applyTransform();
    this.currentIcon = createIcon();
    this.currentIcon.spawn();
    this.observedGeometryRevision = currentIcon.geometryRevision();
    onOpen();
    open = true;
  }

  public void close() {
    open = false;
    if (this.currentIcon != null)
      this.currentIcon.remove();
    onClose();
  }

  public boolean applyIcon(HoloIcon icon) {
    if (!open || currentIcon == null)
      return false;

    if (icon instanceof HoloIcon.Text text
        && currentIcon instanceof TextMenuIcon textIcon
        && textIcon.updateText(text.miniMessage())) {
      currentIcon.applyTransform(location);
      onIconChanged();
      observedGeometryRevision = currentIcon.geometryRevision();
      return true;
    }

    MenuIcon<?> replacement = MenuIcon.createIcon(session, location, ApiMenuTranslator.iconData(icon), this);
    if (replacement == null)
      return false;

    swapIcon(replacement);
    return true;
  }

  protected void swapIcon(MenuIcon<?> icon) {
    this.currentIcon.remove();
    this.currentIcon = icon;
    this.currentIcon.applyTransform(location);
    this.observedGeometryRevision = currentIcon.geometryRevision();
    onIconChanged();
    this.currentIcon.spawn();
  }

  protected void onIconChanged() {
  }

  public void applyTransform() {
    this.location = session.getTransform().componentPosition(offset);
    if (this.currentIcon != null)
      this.currentIcon.applyTransform(location);
  }

  private void refreshDynamicGeometry() {
    long revision = currentIcon.geometryRevision();
    if (revision == observedGeometryRevision) {
      return;
    }
    currentIcon.applyTransform(location);
    onIconChanged();
    observedGeometryRevision = revision;
  }
}
