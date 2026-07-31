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
package art.arcane.holoui.integration;

/**
 * One row of the provider status report. {@code itemCount} is 0 unless the provider is ready, and
 * enumerating it is not free (HeadDatabase exposes tens of thousands of ids), so this is built on
 * demand for the command and never on a menu path.
 */
public record ProviderStatus(String id, String pluginName, boolean pluginPresent, boolean active, boolean ready, int itemCount) {
}
