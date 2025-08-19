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
package com.volmit.holoui.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemUtils {

    public static final class Builder {

        private final ItemStack stack;
        private final ItemMeta meta;

        public Builder(Material m) {
            this(m, 1);
        }

        public Builder(Material m, int amount) {
            this.stack = new ItemStack(m, amount);
            this.meta = stack.getItemMeta();
        }

        public Builder modelData(int data) {
            if (meta == null)
                return this;
            meta.setCustomModelData(data);
            return this;
        }

        public Builder damage(int damage) {
            if (meta == null)
                return this;
            if (meta instanceof Damageable m)
                m.setDamage(damage);
            return this;
        }

        public ItemStack get() {
            if (meta != null)
                stack.setItemMeta(meta);
            return stack;
        }
    }
}
