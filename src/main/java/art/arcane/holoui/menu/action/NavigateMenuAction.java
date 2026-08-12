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

import art.arcane.holoui.config.action.NavigationActionData;

public final class NavigateMenuAction extends MenuAction<NavigationActionData> {
  public NavigateMenuAction(NavigationActionData data) {
    super(data);
  }

  public boolean isValid() {
    return switch (data.modeOrDefault()) {
      case PUSH, REPLACE -> data.target() != null && !data.target().isBlank();
      case BACK, HOME, CLOSE -> true;
    };
  }

  @Override
  public ActionOutcome execute(ActionContext context) {
    if (!isValid()) {
      return ActionOutcome.STOP;
    }
    context.navigate(new NavigationRequest(data.modeOrDefault(), data.target()));
    return ActionOutcome.STOP;
  }
}
