/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package art.arcane.holoui.menu.action;

import art.arcane.holoui.enums.NavigationMode;

import java.util.Objects;

public record NavigationRequest(NavigationMode mode, String target) {
  public NavigationRequest {
    Objects.requireNonNull(mode, "mode");
    if ((mode == NavigationMode.PUSH || mode == NavigationMode.REPLACE)
        && (target == null || target.isBlank())) {
      throw new IllegalArgumentException("Navigation target is required for " + mode.name().toLowerCase());
    }
  }
}
