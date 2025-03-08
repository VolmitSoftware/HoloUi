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
package com.volmit.holoui.menu.special.inventories;

import com.volmit.holoui.config.MenuComponentData;
import com.volmit.holoui.config.components.ComponentData;
import com.volmit.holoui.config.icon.TextIconData;
import com.volmit.holoui.enums.MenuComponentType;
import com.volmit.holoui.menu.MenuSession;
import com.volmit.holoui.menu.components.MenuComponent;
import com.volmit.holoui.menu.icon.MenuIcon;
import com.volmit.holoui.menu.icon.TextMenuIcon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.inventory.Inventory;

import java.util.function.Function;

public class InventoryProgressComponent extends MenuComponent<InventoryProgressComponent.Data> {

    private final Inventory inventory;
    private final Function<Inventory, Double> progress;
    private final int segmentCount;
    private final Style color;

    private double prevProg;

    public InventoryProgressComponent(MenuSession session, MenuComponentData data) {
        super(session, data);
        this.inventory = this.data.inv();
        this.progress = this.data.progress();
        this.segmentCount = this.data.segments();
        this.color = this.data.color();
    }

    @Override
    protected void onTick() {
        double progress = this.progress.apply(inventory);
        if (prevProg == progress)
            return;
        ((TextMenuIcon) currentIcon).updateName(0, getBar(progress));
        this.prevProg = progress;
    }

    @Override
    protected MenuIcon<?> createIcon() {
        return MenuIcon.createIcon(session, location, new TextIconData(""), this);
    }

    protected void onOpen() {
        double progress = this.progress.apply(inventory);
        ((TextMenuIcon) currentIcon).updateName(0, getBar(progress));
        this.prevProg = progress;
    }

    protected void onClose() {
    }

    private Component getBar(double invProgress) {
        int progress = (int) (invProgress * segmentCount);
        var c = Component.text();
        for (int i = 0; i < segmentCount; i++) {
            Style s = i < progress ? this.color : Style.style(NamedTextColor.DARK_GRAY);
            c.append(Component.text("|").style(s));
        }

        return c.build();
    }

    public record Data(Inventory inv, Function<Inventory, Double> progress, int segments,
                       Style color) implements ComponentData {
        public MenuComponentType getType() {
            return null;
        }
    }
}
