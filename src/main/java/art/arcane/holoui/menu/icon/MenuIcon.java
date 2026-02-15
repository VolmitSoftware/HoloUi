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
import art.arcane.holoui.config.icon.*;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.ArmorStandManager;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.util.common.math.CollisionPlane;
import lombok.NonNull;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public abstract class MenuIcon<D extends MenuIconData> {

    protected static final float NAMETAG_SIZE = 1 / 16F * 3.5F;

    protected final MenuSession session;
    protected final D data;

    protected List<UUID> armorStands;
    protected Location position;

    public MenuIcon(MenuSession session, Location loc, D data) throws MenuIconException {
        this.session = session;
        this.position = loc.clone();
        this.data = data;
    }

    @NonNull
    public static MenuIcon<?> createIcon(MenuSession session, Location loc, MenuIconData data, MenuComponent<?> component) {
        try {
            if (data instanceof ItemIconData d)
                return new ItemMenuIcon(session, loc, d);
            else if (data instanceof TextImageIconData d)
                return new TextImageMenuIcon(session, loc, d);
            else if (data instanceof TextIconData d)
                return new TextMenuIcon(session, loc, d);
            else if (data instanceof AnimatedImageData d)
                return new AnimatedTextImageMenuIcon(session, loc, d);
            return new TextImageMenuIcon(session, loc);
        } catch (MenuIconException e) {
            HoloUI.logExceptionStack(false, e, "An error occurred while creating a Menu Icon for the component \"%s\":", component.getId());
            HoloUI.log(Level.WARNING, "Falling back to missing icon.");
            try {
                return new TextImageMenuIcon(session, loc);
            } catch (MenuIconException ignored) {
                //noinspection ConstantConditions
                return null;
            }
        }
    }

    protected abstract List<UUID> createArmorStands(Location loc);

    public abstract CollisionPlane createBoundingBox();

    public void tick() {
    }

    public void spawn() {
        armorStands = createArmorStands(position.clone().subtract(0, NAMETAG_SIZE, 0));
        armorStands.forEach(a -> ArmorStandManager.spawn(a, session.getPlayer()));
    }

    public void remove() {
        armorStands.forEach(ArmorStandManager::delete);
        armorStands.clear();
    }

    public void move(Vector offset) {
        if (armorStands != null && !armorStands.isEmpty())
            armorStands.forEach(a -> ArmorStandManager.move(a, offset));
        this.position.add(offset);
    }

    public void rotate(float yaw) {
        if (armorStands != null && !armorStands.isEmpty())
            armorStands.forEach(a -> ArmorStandManager.rotate(a, yaw));
    }

    public void teleport(Location loc) {
        Vector offset = loc.toVector().subtract(position.toVector());
        move(offset);
    }
}
