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
import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.action.ActionContext;
import art.arcane.holoui.menu.action.SessionActionContext;
import art.arcane.holoui.menu.components.MenuComponent;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;

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
  private final List<MenuComponent<?>> components;

  @Getter(AccessLevel.NONE)
  private final Map<String, MenuComponent<?>> componentsById;

  private final ApiMenuHandle apiHandle;
  private final MenuSessionOptions options;

  private MenuTransform transform;
  private float scaleMultiplier;

  public MenuSession(MenuDefinitionData data, Player p, MenuSessionOptions options) {
    this.id = data.getId();
    this.player = p;
    this.options = Objects.requireNonNull(options, "options");
    this.apiHandle = options.apiHandle();
    this.scaleMultiplier = options.scaleMultiplier();
    this.freezePlayer = data.isLockPosition();
    this.followPlayer = data.isFollowPlayer();
    this.maxDistance = data.getMaxDistance();
    this.closeOnDeath = data.isCloseOnDeath();
    this.closeOnTeleport = data.isCloseOnTeleport();
    this.offsetDistance = data.getOffset().lengthSquared();

    this.transform = options.transform();
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
    this.transform = transform.withAnchor(loc);
    components.forEach(MenuComponent::applyTransform);
  }

  public void follow(Location loc) {
    this.transform = transform.withAnchorAndFacing(loc, loc.getYaw());
    components.forEach(MenuComponent::applyTransform);
  }

  public void refreshScale() {
    this.transform = transform.withScale(scaleMultiplier * HuiSettings.uiScale());
    components.forEach(MenuComponent::applyTransform);
  }

  public void applyTransform(MenuTransform nextTransform, float nextScaleMultiplier) {
    if (!Float.isFinite(nextScaleMultiplier) || nextScaleMultiplier <= 0F) {
      throw new IllegalArgumentException("nextScaleMultiplier must be finite and greater than zero");
    }
    this.transform = Objects.requireNonNull(nextTransform, "nextTransform");
    this.scaleMultiplier = nextScaleMultiplier;
    components.forEach(MenuComponent::applyTransform);
  }

  public void tick() {
    drainApiUpdates();
    components.forEach(MenuComponent::tick);
  }

  public void open() {
    if (options.faceViewerOnOpen()) {
      this.transform = transform.withAnchorAndFacing(transform.anchor(), player.getEyeLocation().getYaw());
    }
    components.forEach(MenuComponent::open);
  }

  public ActionContext actionContext(String componentId, HoloClickTrigger trigger) {
    return new SessionActionContext(this, componentId, options.navigator(), trigger);
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

  public Location getCenterPoint() {
    return transform.menuOrigin();
  }

  public boolean isValid(Location loc) {
    Location centerPoint = getCenterPoint();
    return centerPoint.getWorld() != null
        && Objects.equals(loc.getWorld(), centerPoint.getWorld())
        && centerPoint.distanceSquared(loc) <= maxDistance * maxDistance + offsetDistance;
  }
}
