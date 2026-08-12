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

import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.config.icon.ItemStackIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuTransform;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.ItemUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.registry.RegistryUtil;
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

public class ItemMenuIcon extends MenuIcon<MenuIconData> {

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
    this(session, loc, data, buildStack(data));
  }

  public ItemMenuIcon(MenuSession session, Location loc, ItemStackIconData data) throws MenuIconException {
    this(session, loc, data, data.stack().clone());
  }

  public ItemMenuIcon(MenuSession session, Location loc, MenuIconData data, ItemStack item) throws MenuIconException {
    super(session, loc, data);
    this.item = item;
  }

  private static ItemStack buildStack(ItemIconData data) throws MenuIconException {
    Material material = data.requireMaterial();
    try {
      return new ItemUtils.Builder(material, data.count() > 0 ? data.count() : 1)
          .modelData(data.customModelValue())
          .get();
    } catch (RuntimeException e) {
      MenuIconException ex = new MenuIconException("Unable to build an item icon from \"%s\"", material);
      ex.initCause(e);
      throw ex;
    }
  }

  public CollisionPlane createBoundingBox(Location anchor) {
    float scale = uiScale();
    Vector center = session.getTransform().localPosition(anchor, new Vector(0F, -0.05F, 0F)).toVector();
    return session.getTransform().createPlane(
        center,
        .75F * scale * style.scaleX(),
        .75F * scale * style.scaleY()
    );
  }

  protected List<UUID> createDisplayEntities(Location loc) {
    List<UUID> uuids = Lists.newArrayList();
    float countOffset = item.getAmount() > 1 ? 0F : .09F;
    MenuTransform transform = session.getTransform();
    Location location;
    if (isBlock()) {
      location = blockLocation(transform);
    } else {
      location = transform.localPosition(loc, new Vector(0F, -(ITEM_OFFSET + countOffset), 0F));
    }
    uuids.add(DisplayEntityManager.add(itemDisplay(item, location)));
    if (item.getAmount() > 1) {
      Component count = countText(item.getAmount());
      uuids.add(DisplayEntityManager.add(textDisplay(count, countLocation())));
    }
    return uuids;
  }

  public void updateCount(int count) {
    if (displayEntities.size() == 1 && count > 1) {
      DisplayEntityManager.move(displayEntities.get(0), session.getTransform().localVector(new Vector(0F, .09F, 0F)));
      UUID displayEntity = DisplayEntityManager.add(textDisplay(countText(count), countLocation()));
      displayEntities.add(displayEntity);
      applyOrientation();
      DisplayEntityManager.spawn(displayEntity, session.getPlayer());
    } else if (displayEntities.size() == 2 && count < 2) {
      DisplayEntityManager.move(displayEntities.get(0), session.getTransform().localVector(new Vector(0F, -.09F, 0F)));
      DisplayEntityManager.delete(displayEntities.get(1), session.getPlayer());
      displayEntities.remove(1);
    } else {
      DisplayEntityManager.changeName(displayEntities.get(1), countText(count));
    }
  }

  @Override
  protected void applyOrientation() {
    super.applyOrientation();
    if (isBlock() && displayEntities != null && !displayEntities.isEmpty()) {
      DisplayEntityManager.goTo(displayEntities.getFirst(), blockLocation(session.getTransform()));
    }
  }

  private boolean isBlock() {
    return item.getType().isBlock() && !BLOCK_BLACKLIST.contains(item.getType());
  }

  private Location countLocation() {
    return session.getTransform().localPosition(position, new Vector(0F, -localLineHeight() - .37F, 0F));
  }

  private Location blockLocation(MenuTransform transform) {
    return transform.localPosition(position, new Vector(0F, BLOCK_OFFSET, .3F));
  }

  private Component countText(int count) {
    return Component.text(count).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
  }
}
