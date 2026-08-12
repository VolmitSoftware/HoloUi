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

import art.arcane.holoui.config.icon.BlockIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.math.CollisionPlane;
import com.github.retrooper.packetevents.util.Vector3f;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public final class BlockMenuIcon extends MenuIcon<BlockIconData> {
  static final float DISPLAY_SIZE = 0.75F;
  static final float VERTICAL_OFFSET = -0.05F;

  private final BlockData blockData;

  public BlockMenuIcon(MenuSession session, Location loc, BlockIconData data) throws MenuIconException {
    super(session, loc, data);
    Material material = data.requireBlock();
    try {
      this.blockData = material.createBlockData();
    } catch (RuntimeException | LinkageError failure) {
      MenuIconException exception = new MenuIconException(
          "Unable to create the default block state for \"%s\"", material.getKey());
      exception.initCause(failure);
      throw exception;
    }
  }

  @Override
  protected List<UUID> createDisplayEntities(Location loc) {
    Location center = session.getTransform().localPosition(
        position,
        new Vector(0F, VERTICAL_OFFSET, 0F)
    );
    DisplayEntity displayEntity = applyStyle(DisplayEntity.Builder.blockDisplay(blockData, center));
    Vector3f styledScale = displayEntity.scale();
    float scaleX = styledScale.getX() * DISPLAY_SIZE;
    float scaleY = styledScale.getY() * DISPLAY_SIZE;
    float scaleZ = styledScale.getZ() * DISPLAY_SIZE;
    displayEntity.scale(new Vector3f(scaleX, scaleY, scaleZ));
    displayEntity.translation(new Vector3f(-scaleX / 2F, -scaleY / 2F, -scaleZ / 2F));
    return List.of(DisplayEntityManager.add(displayEntity));
  }

  @Override
  public CollisionPlane createBoundingBox(Location anchor) {
    float scale = uiScale();
    Vector center = session.getTransform().localPosition(
        anchor,
        new Vector(0F, VERTICAL_OFFSET, 0F)
    ).toVector();
    return session.getTransform().createPlane(
        center,
        DISPLAY_SIZE * scale * style.scaleX(),
        DISPLAY_SIZE * scale * style.scaleY()
    );
  }
}
