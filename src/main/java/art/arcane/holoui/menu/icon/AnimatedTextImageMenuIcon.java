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
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class AnimatedTextImageMenuIcon extends MenuIcon<AnimatedImageData> {

  private final LinkedList<List<Component>> frameComponents = Lists.newLinkedList();

  private int currentFrame;
  private int passedTicks;

  public AnimatedTextImageMenuIcon(MenuSession session, Location loc, AnimatedImageData data) throws MenuIconException {
    super(session, loc, data);
    createComponents();
    currentFrame = passedTicks = 0;
  }

  @Override
  public void tick() {
    passedTicks++;
    if (passedTicks >= data.speed()) {
      passedTicks = 0;
      currentFrame = ++currentFrame % frameComponents.size();
      updateFrame();
    }
  }

  @Override
  protected List<UUID> createDisplayEntities(Location location) {
    List<UUID> uuids = Lists.newArrayList();
    float lineHeight = scaledTagSize();
    float scale = uiScale();
    location.add(0, ((frameComponents.getFirst().size() - 1) / 2F * lineHeight) - lineHeight, 0);
    frameComponents.getFirst().forEach(c -> {
      uuids.add(DisplayEntityManager.add(DisplayEntity.Builder.textDisplay(c, location, scale, billboardMode(), (byte) 0, 0)));
      location.subtract(0, lineHeight, 0);
    });
    return uuids;
  }

  @Override
  public CollisionPlane createBoundingBox() {
    float lineHeight = scaledTagSize();
    float width = 0;
    for (Component component : frameComponents.getFirst())
      width = Math.max(width, TextUtils.content(component).length() * lineHeight / 2F);
    return new CollisionPlane(position.toVector(), width, (frameComponents.getFirst().size() - 1) * lineHeight);
  }

  private List<BufferedImage> getImages() throws IOException {
    List<BufferedImage> images = Lists.newArrayList();
    for (String s : data.source())
      images.add(HoloUI.INSTANCE.getConfigManager().getImage(s).getRight());
    return images;
  }

  private void createComponents() throws MenuIconException {
    try {
      int height = getImages()
          .stream()
          .mapToInt(BufferedImage::getHeight)
          .max()
          .orElse(0);
      getImages().forEach(i -> {
        List<Component> lines = Lists.newArrayList();
        for (int y = 0; y < i.getHeight(); y++) {
          var component = Component.text();
          for (int x = 0; x < i.getWidth(); x++) {
            int colour = i.getRGB(x, y);
            if (((colour >> 24) & 0x0000FF) < 255)
              component.append(Component.text(" ").decorate(TextDecoration.BOLD))
                  .append(Component.text(" "));
            else
              component.append(TextUtils.textColor("█", colour & 0x00FFFFFF));
          }
          lines.add(component.build());
        }

        var empty = Component.text();
        for (int x = 0; x < i.getWidth(); x++) {
          empty.append(Component.text(" ")
                  .decorate(TextDecoration.BOLD))
              .append(Component.text(" "));
        }
        for (int y = 0; y < height - i.getHeight(); y++) {
          lines.add(empty.build());
        }

        frameComponents.add(lines);
      });
    } catch (IOException e) {
      MenuIconException ex = new MenuIconException("Failed to construct animated icon!");
      ex.initCause(e);
      throw ex;
    }
  }

  private void updateFrame() {
    List<Component> components = frameComponents.get(currentFrame);
    for (int i = 0; i < displayEntities.size(); i++)
      DisplayEntityManager.changeName(displayEntities.get(i), components.get(i));
  }
}
