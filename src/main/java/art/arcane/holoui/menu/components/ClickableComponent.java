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

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.util.common.ParticleUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.Events;
import art.arcane.volmlib.util.math.MathHelper;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

public abstract class ClickableComponent<T extends ComponentData> extends MenuComponent<T> {

  private final float highlightMod;

  protected CollisionPlane plane;
  protected boolean selected;

  private Events click;

  public ClickableComponent(MenuSession session, MenuComponentData data, float highlightMod) {
    super(session, data);
    this.highlightMod = highlightMod;
  }

  public abstract void onClick();

  @Override
  public void onOpen() {
    this.plane = currentIcon.createBoundingBox();
    click = Events.listen(HoloUI.INSTANCE, PlayerInteractEvent.class, EventPriority.MONITOR, e -> {
      if (session.getPlayer().equals(e.getPlayer()) && selected) {
        if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) {
          onClick();
          e.setCancelled(true);
        }
      }
    });
  }

  @Override
  protected void onTick() {
    Location playerPos = session.getPlayer().getEyeLocation().clone();
    rotateToFace(playerPos);
    boolean isLookingAt = plane.isLookingAt(playerPos.toVector(), playerPos.getDirection());
    if (isLookingAt && !selected) {
      this.selected = true;
      currentIcon.move(plane.getNormal().clone().multiply(highlightMod));
    } else if (!isLookingAt && selected) {
      this.selected = false;
      currentIcon.teleport(location);
    }
  }

  @Override
  public void onClose() {
    click.unregister();
  }

  @Override
  public void move(Location loc) {
    super.move(loc);
    this.plane.move(location);
  }

  @Override
  public void adjustRotation(boolean byPlayer) {
    super.adjustRotation(byPlayer);
    if (this.plane != null) {
      this.plane.move(location);
    }
  }

  public void highlightHitbox(World w) {
    if (plane == null)
      return;
    Vector downRight = plane.getCenter().clone().subtract(plane.getUp().clone().multiply(plane.getHeight() / 2)).add(plane.getRight().clone().multiply(plane.getWidth() / 2));
    Vector downLeft = plane.getCenter().clone().subtract(plane.getUp().clone().multiply(plane.getHeight() / 2)).subtract(plane.getRight().clone().multiply(plane.getWidth() / 2));
    Vector upRight = plane.getCenter().clone().add(plane.getUp().clone().multiply(plane.getHeight() / 2)).add(plane.getRight().clone().multiply(plane.getWidth() / 2));
    Vector upLeft = plane.getCenter().clone().add(plane.getUp().clone().multiply(plane.getHeight() / 2)).subtract(plane.getRight().clone().multiply(plane.getWidth() / 2));
    for (float d = .1F; d <= 1; d += .1F) {
      ParticleUtils.playParticle(w, MathHelper.interpolate(downRight, upRight, d), Color.BLUE);
      ParticleUtils.playParticle(w, MathHelper.interpolate(downLeft, upLeft, d), Color.BLUE);
      ParticleUtils.playParticle(w, MathHelper.interpolate(downLeft, downRight, d), Color.BLUE);
      ParticleUtils.playParticle(w, MathHelper.interpolate(upLeft, upRight, d), Color.BLUE);
      ParticleUtils.playParticle(w, MathHelper.interpolate(plane.getCenter(), plane.getCenter().clone().add(plane.getNormal().clone().multiply(2)), d), Color.RED);
    }
    ParticleUtils.playParticle(w, downRight, Color.BLUE);
    ParticleUtils.playParticle(w, downLeft, Color.BLUE);
    ParticleUtils.playParticle(w, upRight, Color.BLUE);
    ParticleUtils.playParticle(w, upLeft, Color.BLUE);
  }

  private void rotateToFace(Location loc) {
    Vector rotation = MathHelper.getRotationFromDirection(MathHelper.unit(loc.toVector(), plane.getCenter()));
    plane.rotate((float) rotation.getX(), (float) -rotation.getY());
    if (selected)
      currentIcon.teleport(location.clone().add(plane.getNormal()));
  }

}
