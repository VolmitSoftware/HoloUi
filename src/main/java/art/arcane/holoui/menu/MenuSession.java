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
package art.arcane.holoui.menu;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.volmlib.util.math.MathHelper;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

@Getter
public class MenuSession {

  private final String id;
  private final Player player;
  private final boolean freezePlayer, followPlayer;
  private final boolean closeOnDeath, closeOnTeleport;
  private final double maxDistance;
  private final double offsetDistance;
  private final Vector offset;
  private final List<MenuComponent<?>> components;

  @Getter(AccessLevel.NONE)
  private final Map<String, MenuComponent<?>> componentsById;

  private final ApiMenuHandle apiHandle;

  protected Location centerPoint;
  protected float initialY = Float.NaN;

  public MenuSession(MenuDefinitionData data, Player p) {
    this(data, p, null);
  }

  public MenuSession(MenuDefinitionData data, Player p, ApiMenuHandle apiHandle) {
    this.id = data.getId();
    this.player = p;
    this.apiHandle = apiHandle;
    this.freezePlayer = data.isLockPosition();
    this.followPlayer = data.isFollowPlayer();
    this.maxDistance = data.getMaxDistance();
    this.closeOnDeath = data.isCloseOnDeath();
    this.closeOnTeleport = data.isCloseOnTeleport();
    this.offset = data.getOffset().clone().multiply(new Vector(-1, 1, 1));
    this.offsetDistance = offset.lengthSquared();

    this.centerPoint = p.getLocation().clone().add(offset);
    Map<String, MenuComponent<?>> uniqueComponents = new LinkedHashMap<>(data.getComponents().size());
    for (MenuComponentData componentData : data.getComponents()) {
      MenuComponent<?> component = componentData.createComponent(this);
      if (component == null) {
        continue;
      }
      if (uniqueComponents.putIfAbsent(component.getId(), component) != null) {
        HoloUI.log(Level.WARNING, "Menu \"%s\" declares duplicate component id \"%s\"; keeping the first component.",
            id, component.getId());
      }
    }
    this.components = List.copyOf(new ArrayList<>(uniqueComponents.values()));
    this.componentsById = uniqueComponents;
  }

  public void drainApiUpdates() {
    ApiMenuHandle handle = apiHandle;
    if (handle == null || !handle.dirty()) {
      return;
    }

    handle.drain((componentId, icon) -> {
      MenuComponent<?> component = componentsById.get(componentId);
      if (component == null) {
        return;
      }

      try {
        component.applyIcon(icon);
      } catch (Exception ex) {
        HoloUI.logExceptionStack(false, ex, "Failed to apply an API icon update to component %s of menu %s for %s.",
            componentId, id, player.getName());
      }
    });
  }

  public void move(Location loc) {
    this.centerPoint = loc.add(offset);
    components.forEach(c -> {
      c.move(this.centerPoint.clone());
      c.adjustRotation();
    });
  }

  public void open() {
    this.initialY = -player.getEyeLocation().getYaw();
    components.forEach(MenuComponent::open);
  }

  public void close() {
    for (MenuComponent<?> component : components) {
      try {
        component.close();
      } catch (Exception ex) {
        HoloUI.logExceptionStack(false, ex, "Failed to close menu component %s for %s.", component.getId(), player.getName());
      }
    }
  }

  public Location getCenterInitialYAdjusted() {
    return MathHelper.rotateAroundPoint(centerPoint.clone(), player.getEyeLocation(), 0, initialY);
  }

  public boolean isValid(Location loc) {
    return centerPoint.getWorld() != null
        && Objects.equals(loc.getWorld(), centerPoint.getWorld())
        && centerPoint.distanceSquared(loc) <= maxDistance * maxDistance + offsetDistance;
  }
}
