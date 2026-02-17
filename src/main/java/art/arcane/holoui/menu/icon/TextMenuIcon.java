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

import com.google.common.collect.Lists;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.holoui.util.common.math.CollisionPlane;
import art.arcane.volmlib.util.bukkit.Placeholders;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TextMenuIcon extends MenuIcon<TextIconData> {

    private final List<Component> components;

    public TextMenuIcon(MenuSession session, Location loc, TextIconData data) throws MenuIconException {
        super(session, loc, data);
        components = Arrays.stream(data.text().split("\n"))
                .map(s -> TextUtils.parse(Placeholders.setPlaceholders(session.getPlayer(), s)))
                .collect(Collectors.toList());
    }

    @Override
    protected List<UUID> createDisplayEntities(Location loc) {
        List<UUID> uuids = Lists.newArrayList();
        float lineHeight = scaledTagSize();
        float scale = uiScale();
        loc.add(0, ((components.size() - 1) / 2F * lineHeight) - lineHeight, 0);
        components.forEach(c -> {
            uuids.add(DisplayEntityManager.add(DisplayEntity.Builder.textDisplay(c, loc, scale, billboardMode(), textFlags(), textBackgroundColor())));
            loc.subtract(0, lineHeight, 0);
        });
        return uuids;
    }

    @Override
    public CollisionPlane createBoundingBox() {
        float lineHeight = scaledTagSize();
        float width = 0;
        for (Component component : components)
            width = Math.max(width, TextUtils.content(component).length() * lineHeight / 2F);
        return new CollisionPlane(position.toVector(), width, components.size() * lineHeight);
    }

    public void updateName(int index, Component c) {
        if (index >= components.size())
            return;
        components.set(index, c);
        DisplayEntityManager.changeName(this.displayEntities.get(index), c);
    }
}
