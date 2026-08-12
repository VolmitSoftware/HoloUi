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

import art.arcane.holoui.api.HoloClickTrigger;
import org.bukkit.entity.Player;

public interface ActionContext {
  Player player();

  String menuId();

  String componentId();

  HoloClickTrigger trigger();

  NavigationResult navigate(NavigationRequest request);
}
