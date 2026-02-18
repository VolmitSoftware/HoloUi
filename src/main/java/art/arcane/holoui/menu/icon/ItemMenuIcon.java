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

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.special.BlockMenuSession;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.ItemUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.registry.RegistryUtil;
import art.arcane.volmlib.util.math.MathHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class ItemMenuIcon extends MenuIcon<ItemIconData> {

  private static final float ITEM_OFFSET = 1F;
  private static final float BLOCK_OFFSET = -.95F;
  private static final Material GRASS = RegistryUtil.find(Material.class, "grass", "short_grass");
  private static final List<Material> BLOCK_BLACKLIST = ImmutableList.of(
      Material.BARRIER, Material.LIGHT, Material.HOPPER, Material.TURTLE_EGG, GRASS, Material.TALL_GRASS,
      Material.WHITE_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
      Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
      Material.PINK_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
      Material.CYAN_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
      Material.BROWN_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
      Material.BLACK_STAINED_GLASS_PANE, Material.GLASS_PANE, Material.POPPY, Material.DANDELION);
  private final ItemStack item;

  public ItemMenuIcon(MenuSession session, Location loc, ItemIconData data) throws MenuIconException {
    super(session, loc, data);
    this.item = new ItemUtils.Builder(data.materialType(), data.count() > 0 ? data.count() : 1)
        .modelData(data.customModelValue())
        .get();
  }

  public CollisionPlane createBoundingBox() {
    float scale = uiScale();
    return new CollisionPlane(position.toVector().clone().subtract(new Vector(0, 0.05F * scale, 0)), .75F * scale, .75F * scale);
  }

  protected List<UUID> createDisplayEntities(Location loc) {
    List<UUID> uuids = Lists.newArrayList();
    float scale = uiScale();
    float countScale = countScale();
    byte displayType = session instanceof BlockMenuSession ? (byte) 8 : (byte) 0;
    float countOffset = session instanceof BlockMenuSession ? 0F : (item.getAmount() > 1 ? 0F : .09F);
    Location location = loc.clone();
    if (isBlock())
      location.add(0, BLOCK_OFFSET * scale, 0);
    else
      location.subtract(0, (ITEM_OFFSET + countOffset) * scale, 0);
    uuids.add(DisplayEntityManager.add(DisplayEntity.Builder.itemDisplay(item, location, scale, billboardMode(), displayType)));
    if (item.getAmount() > 1) {
      Component count = countText(item.getAmount());
      uuids.add(DisplayEntityManager.add(DisplayEntity.Builder.textDisplay(count, countLocation(), countScale, billboardMode(), textFlags(), textBackgroundColor())));
    }
    return uuids;
  }

  public void updateCount(int count) {
    float scale = uiScale();
    float countScale = countScale();
    if (displayEntities.size() == 1 && count > 1) {
      if (!(session instanceof BlockMenuSession)) {
        DisplayEntityManager.move(displayEntities.get(0), new Vector(0, .09F * scale, 0));
      }
      UUID displayEntity = DisplayEntityManager.add(DisplayEntity.Builder.textDisplay(countText(count), countLocation(), countScale, billboardMode(), textFlags(), textBackgroundColor()));
      displayEntities.add(displayEntity);
      DisplayEntityManager.spawn(displayEntity, session.getPlayer());
    } else if (displayEntities.size() == 2 && count < 2) {
      if (!(session instanceof BlockMenuSession)) {
        DisplayEntityManager.move(displayEntities.get(0), new Vector(0, -.09F * scale, 0));
      }
      DisplayEntityManager.delete(displayEntities.get(1));
      displayEntities.remove(1);
    } else {
      DisplayEntityManager.changeName(displayEntities.get(1), countText(count));
    }
  }

  @Override
  public void spawn() {
    super.spawn();
    rotate((float) MathHelper.getRotationFromDirection(session.getPlayer().getEyeLocation().getDirection().multiply(-1F)).getY());
    Vector dir = session.getPlayer().getEyeLocation().getDirection();
    rotate(-(float) MathHelper.getRotationFromDirection(dir).getY());
  }

  @Override
  public void rotate(float yaw) {
    if (session instanceof BlockMenuSession) {
      return;
    }
    if (isBlock()) {
      float scale = uiScale();
      Location offset = MathHelper.rotateAroundPoint(this.position.clone().add(0, BLOCK_OFFSET * scale, .3F * scale), this.position, 0, yaw);
      DisplayEntityManager.goTo(displayEntities.get(0), offset);
      super.rotate(-yaw + 180);
    } else
      super.rotate(-yaw + 180);
  }

  private boolean isBlock() {
    if (session instanceof BlockMenuSession) {
      return false;
    }
    return item.getType().isBlock() && !BLOCK_BLACKLIST.contains(item.getType());
  }

  private float countScale() {
    float scale = uiScale();
    if (session instanceof BlockMenuSession) {
      float iconScale = HuiSettings.previewIconScale();
      if (iconScale > 0F) {
        scale *= HuiSettings.previewTextScale() / iconScale;
      }
    }
    return scale;
  }

  private Location countLocation() {
    float scale = uiScale();
    if (session instanceof BlockMenuSession) {
      Location count = position.clone().add(.18F * scale, -scaledTagSize() - (.20F * scale), 0F);
      Vector towardEye = session.getPlayer().getEyeLocation().toVector().subtract(count.toVector());
      if (towardEye.lengthSquared() > 0.0D) {
        count.add(towardEye.normalize().multiply(.08F * scale));
      }
      return count;
    }
    return position.clone().add(0F, -scaledTagSize() - (.37F * scale), 0F);
  }

  private Component countText(int count) {
    return Component.text(count).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
  }
}
