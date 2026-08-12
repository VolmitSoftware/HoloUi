/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package art.arcane.holoui.config.action;

import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.enums.MenuActionType;
import art.arcane.holoui.enums.NavigationMode;

public record NavigationActionData(String target, NavigationMode mode,
                                   HoloClickTrigger trigger) implements MenuActionData {
  @Override
  public MenuActionType getType() {
    return MenuActionType.NAVIGATE;
  }

  public NavigationMode modeOrDefault() {
    return mode == null ? NavigationMode.PUSH : mode;
  }
}
