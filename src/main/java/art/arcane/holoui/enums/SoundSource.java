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
package art.arcane.holoui.enums;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.SoundCategory;

@AllArgsConstructor
@Getter
public enum SoundSource {
    @SerializedName("master") MASTER(SoundCategory.MASTER),
    @SerializedName("music") MUSIC(SoundCategory.MUSIC),
    @SerializedName("record") RECORD(SoundCategory.RECORDS),
    @SerializedName("weather") WEATHER(SoundCategory.WEATHER),
    @SerializedName("block") BLOCK(SoundCategory.BLOCKS),
    @SerializedName("hostile") HOSTILE(SoundCategory.HOSTILE),
    @SerializedName("neutral") NEUTRAL(SoundCategory.NEUTRAL),
    @SerializedName("player") PLAYER(SoundCategory.PLAYERS),
    @SerializedName("ambient") AMBIENT(SoundCategory.AMBIENT),
    @SerializedName("voice") VOICE(SoundCategory.VOICE);

    private final SoundCategory category;
}
