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
package art.arcane.holoui.config;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.util.common.settings.EntryType;
import art.arcane.holoui.util.common.settings.Settings;

import java.io.File;

public class HuiSettings extends Settings {

    public static final Entry<Boolean> DEBUG_HITBOX = new Entry<>(EntryType.BOOLEAN, false, b -> HoloUI.INSTANCE.getSessionManager().controlHitboxDebug(b));
    public static final Entry<Boolean> DEBUG_SPACING = new Entry<>(EntryType.BOOLEAN, false, b -> HoloUI.INSTANCE.getSessionManager().controlPositionDebug(b));
    public static final Entry<String> BUILDER_IP = new Entry<>(EntryType.STRING, "0.0.0.0", b -> {
    });
    public static final Entry<Integer> BUILDER_PORT = new Entry<>(EntryType.INTEGER, 8080, i -> {
    });
    public static final Entry<Boolean> PREVIEW_FOLLOW_PLAYER = new Entry<>(EntryType.BOOLEAN, false, i -> {
    });
    public static final Entry<Boolean> PREVIEW_ENABLED = new Entry<>(EntryType.BOOLEAN, true, i -> {
    });
    public static final Entry<Boolean> PREVIEW_BY_PERMISSION = new Entry<>(EntryType.BOOLEAN, true, i -> {});

    public HuiSettings(File configDir) {
        super(new File(configDir, "settings.json"));
    }

    @Override
    protected void registerFields() {
        registerField("debugHitbox", DEBUG_HITBOX);
        registerField("debugPosition", DEBUG_SPACING);
        registerField("builderIp", BUILDER_IP);
        registerField("builderPort", BUILDER_PORT);

        registerField("previewFollowPlayer", PREVIEW_FOLLOW_PLAYER);
        registerField("previewEnabled", PREVIEW_ENABLED);
        registerField("previewByPermission", PREVIEW_BY_PERMISSION);
    }
}
