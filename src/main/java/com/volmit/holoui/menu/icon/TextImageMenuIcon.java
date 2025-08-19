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
package com.volmit.holoui.menu.icon;

import com.google.common.collect.Lists;
import com.volmit.holoui.HoloUI;
import com.volmit.holoui.config.icon.TextImageIconData;
import com.volmit.holoui.exceptions.MenuIconException;
import com.volmit.holoui.menu.ArmorStandManager;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.utils.ArmorStand;
import com.volmit.holoui.utils.TextUtils;
import com.volmit.holoui.utils.math.CollisionPlane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Location;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class TextImageMenuIcon extends MenuIcon<TextImageIconData> {

    public static final List<Component> MISSING = Lists.newArrayList(
            TextUtils.textColor("████", "#000000").append(TextUtils.textColor("████", "#f800f8")),
            TextUtils.textColor("████", "#000000").append(TextUtils.textColor("████", "#f800f8")),
            TextUtils.textColor("████", "#000000").append(TextUtils.textColor("████", "#f800f8")),
            TextUtils.textColor("████", "#000000").append(TextUtils.textColor("████", "#f800f8")),
            TextUtils.textColor("████", "#f800f8").append(TextUtils.textColor("████", "#000000")),
            TextUtils.textColor("████", "#f800f8").append(TextUtils.textColor("████", "#000000")),
            TextUtils.textColor("████", "#f800f8").append(TextUtils.textColor("████", "#000000")),
            TextUtils.textColor("████", "#f800f8").append(TextUtils.textColor("████", "#000000")));
    private final List<Component> components;

    public TextImageMenuIcon(MenuSession session, Location loc, TextImageIconData data) throws MenuIconException {
        super(session, loc, data);
        components = createComponents();
    }

    public TextImageMenuIcon(MenuSession session, Location loc) throws MenuIconException {
        super(session, loc, null);
        components = MISSING;
    }

    @Override
    protected List<UUID> createArmorStands(Location loc) {
        List<UUID> uuids = Lists.newArrayList();
        loc.add(0, ((components.size() - 1) / 2F * NAMETAG_SIZE) - NAMETAG_SIZE, 0);
        components.forEach(c -> {
            uuids.add(ArmorStandManager.add(ArmorStand.Builder.nametagArmorStand(c, loc)));
            loc.subtract(0, NAMETAG_SIZE, 0);
        });
        return uuids;
    }

    @Override
    public CollisionPlane createBoundingBox() {
        float width = 0;
        for (Component component : components)
            width = Math.max(width, TextUtils.content(component).length() * NAMETAG_SIZE / 2);
        return new CollisionPlane(position.toVector(), width, (components.size() - 1) * NAMETAG_SIZE);
    }

    private List<Component> createComponents() throws MenuIconException {
        try {
            Pair<ImageFormat, BufferedImage> imageData = HoloUI.INSTANCE.getConfigManager().getImage(data.relativePath());
            BufferedImage image = imageData.getRight();
            ImageFormat format = imageData.getLeft();
            List<Component> lines = Lists.newArrayList();
            for (int y = 0; y < image.getHeight(); y++) {
                var component = Component.text();
                for (int x = 0; x < image.getWidth(); x++) {
                    int colour = image.getRGB(x, y);
                    if (format != ImageFormats.JPEG && ((colour >> 24) & 0x0000FF) < 255)
                        component.append(Component.text(" ").decorate(TextDecoration.BOLD)).append(Component.text(" "));
                    else
                        component.append(TextUtils.textColor("█", colour & 0x00FFFFFF));
                }

                lines.add(component.build());
            }
            return lines;
        } catch (IOException e) {
            MenuIconException ex = new MenuIconException("Failed to load relative image \"%s\"!", data.relativePath());
            ex.initCause(e);
            throw ex;
        }
    }
}
