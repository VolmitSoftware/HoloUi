/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package art.arcane.holoui.enums;

import com.google.gson.annotations.SerializedName;

public enum NavigationMode {
  @SerializedName("push") PUSH,
  @SerializedName("replace") REPLACE,
  @SerializedName("back") BACK,
  @SerializedName("home") HOME,
  @SerializedName("close") CLOSE
}
