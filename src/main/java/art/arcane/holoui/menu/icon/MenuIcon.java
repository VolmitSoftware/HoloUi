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
import art.arcane.holoui.config.icon.AnimatedImageData;
import art.arcane.holoui.config.icon.BlockIconData;
import art.arcane.holoui.config.icon.CustomItemIconData;
import art.arcane.holoui.config.icon.EntityIconData;
import art.arcane.holoui.config.icon.IconBillboard;
import art.arcane.holoui.config.icon.IconDisplayStyle;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.config.icon.ItemStackIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.config.icon.TextImageIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.integration.ItemProviderRegistry;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuTransform;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.math.CollisionPlane;
import com.github.retrooper.packetevents.util.Vector3f;
import net.kyori.adventure.text.Component;
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
  protected final IconDisplayStyle style;

  protected List<UUID> displayEntities;
  protected Location position;
  private long geometryRevision;

  public MenuIcon(MenuSession session, Location loc, D data) throws MenuIconException {
    this.session = session;
    this.position = session.getTransform().orient(loc);
    this.data = data;
    this.style = IconDisplayStyle.resolve(data == null ? null : data.style());
  }

  @NonNull
  public static MenuIcon<?> createIcon(MenuSession session, Location loc, MenuIconData data, MenuComponent<?> component) {
    try {
      if (data instanceof ItemIconData d)
        return new ItemMenuIcon(session, loc, d);
      else if (data instanceof BlockIconData d)
        return new BlockMenuIcon(session, loc, d);
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
      else if (data instanceof EntityIconData d)
        return new EntityMenuIcon(session, loc, d);
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

  public void orientHitbox(CollisionPlane plane, Vector viewerPosition) {
    orientBillboardPlane(plane, style.billboard(), viewerPosition);
  }

  static void orientBillboardPlane(CollisionPlane plane, IconBillboard billboard, Vector viewerPosition) {
    if (plane == null || billboard == null || billboard == IconBillboard.FIXED || viewerPosition == null) {
      return;
    }
    Vector toViewer = viewerPosition.clone().subtract(plane.getCenter());
    if (toViewer.lengthSquared() < 1.0E-12D) {
      return;
    }

    if (billboard == IconBillboard.VERTICAL) {
      Vector normal = toViewer.setY(0D);
      if (normal.lengthSquared() < 1.0E-12D) {
        return;
      }
      normal.normalize();
      Vector up = new Vector(0D, 1D, 0D);
      plane.orient(up, normal.clone().crossProduct(up));
      return;
    }

    if (billboard == IconBillboard.HORIZONTAL) {
      Vector right = plane.getRight().clone().normalize();
      Vector normal = toViewer.subtract(right.clone().multiply(toViewer.dot(right)));
      if (normal.lengthSquared() < 1.0E-12D) {
        return;
      }
      normal.normalize();
      plane.orient(right.clone().crossProduct(normal), right);
      return;
    }

    Vector normal = toViewer.normalize();
    Vector referenceUp = Math.abs(normal.dot(new Vector(0D, 1D, 0D))) > 0.999D
        ? plane.getUp().clone()
        : new Vector(0D, 1D, 0D);
    Vector right = normal.clone().crossProduct(referenceUp);
    if (right.lengthSquared() < 1.0E-12D) {
      return;
    }
    right.normalize();
    plane.orient(right.clone().crossProduct(normal), right);
  }

  public void tick() {
  }

  public long geometryRevision() {
    return geometryRevision;
  }

  protected void markGeometryChanged() {
    geometryRevision++;
  }

  protected float uiScale() {
    return session.getTransform().scale();
  }

  protected byte billboardMode() {
    return style.billboard().metadataValue();
  }

  protected byte textFlags() {
    return style.textFlags();
  }

  protected int textBackgroundColor() {
    return style.backgroundArgb().argb();
  }

  protected float localLineHeight() {
    return NAMETAG_SIZE * style.scaleY();
  }

  protected float scaledLineHeight() {
    return NAMETAG_SIZE * uiScale() * style.scaleY();
  }

  protected float scaledCharacterWidth() {
    return NAMETAG_SIZE * uiScale() * style.scaleX();
  }

  protected Vector textBoundingBoxCenter(Location anchor) {
    return textBoundingBoxCenter(session.getTransform(), anchor, style.scaleY());
  }

  static Vector textBoundingBoxCenter(MenuTransform transform, Location anchor) {
    return textBoundingBoxCenter(transform, anchor, 1F);
  }

  static Vector textBoundingBoxCenter(MenuTransform transform, Location anchor, float scaleY) {
    float offset = ((2F * NAMETAG_SIZE) - TEXT_DISPLAY_BASELINE) * scaleY;
    return transform.localPosition(anchor, new Vector(0F, -offset, 0F)).toVector();
  }

  protected DisplayEntity textDisplay(Component component, Location location) {
    DisplayEntity displayEntity = DisplayEntity.Builder.textDisplay(component, location);
    return applyTextStyle(displayEntity);
  }

  protected DisplayEntity applyTextStyle(DisplayEntity displayEntity) {
    applyStyle(displayEntity);
    displayEntity.textFlags(textFlags());
    displayEntity.backgroundColor(textBackgroundColor());
    displayEntity.textOpacity((byte) (style.textOpacity() & 0xFF));
    displayEntity.lineWidth(style.lineWidth());
    return displayEntity;
  }

  protected DisplayEntity itemDisplay(ItemStack item, Location location) {
    DisplayEntity displayEntity = DisplayEntity.Builder.itemDisplay(item, location);
    return applyStyle(displayEntity);
  }

  protected DisplayEntity applyStyle(DisplayEntity displayEntity) {
    float scale = uiScale();
    displayEntity.entityFlags((byte) (displayEntity.entityFlags() | style.entityFlags()));
    displayEntity.billboard(billboardMode());
    displayEntity.brightness(style.packedBrightness());
    displayEntity.viewRange(style.viewRange());
    displayEntity.shadowRadius(style.shadowRadius());
    displayEntity.shadowStrength(style.shadowStrength());
    displayEntity.width(style.cullingWidth());
    displayEntity.height(style.cullingHeight());
    displayEntity.glowColorOverride(style.glowColorOverride());
    displayEntity.scale(new Vector3f(
        scale * style.scaleX(),
        scale * style.scaleY(),
        scale * style.scaleZ()
    ));
    return displayEntity;
  }

  public void spawn() {
    Location spawnLocation = session.getTransform().localPosition(position, new Vector(0F, -localLineHeight(), 0F));
    displayEntities = createDisplayEntities(spawnLocation);
    applyOrientation();
    displayEntities.forEach(entity -> DisplayEntityManager.spawn(entity, session.getPlayer()));
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

  public void applyTransform(Location loc) {
    teleport(session.getTransform().orient(loc));
    applyOrientation();
  }

  public void teleport(Location loc) {
    Location target = loc.clone();
    Vector offset = target.toVector().subtract(position.toVector());
    move(offset);
    this.position.setYaw(target.getYaw());
    this.position.setPitch(target.getPitch());
  }

  protected void applyOrientation() {
    if (billboardMode() != 0 || displayEntities == null || displayEntities.isEmpty()) {
      return;
    }
    MenuTransform transform = session.getTransform();
    displayEntities.forEach(entity -> DisplayEntityManager.orient(
        entity,
        transform.displayYaw(),
        transform.pitch(),
        transform.roll()
    ));
  }
}
