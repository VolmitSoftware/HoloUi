/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package art.arcane.holoui.menu;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.action.MenuNavigator;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

public record MenuSessionOptions(ApiMenuHandle apiHandle, MenuTransform transform,
                                 boolean faceViewerOnOpen, MenuNavigator navigator,
                                 float scaleMultiplier) {
  public MenuSessionOptions {
    transform = Objects.requireNonNull(transform, "transform");
    navigator = Objects.requireNonNull(navigator, "navigator");
    if (!Float.isFinite(scaleMultiplier) || scaleMultiplier <= 0F) {
      throw new IllegalArgumentException("scaleMultiplier must be finite and greater than zero");
    }
  }

  public static MenuSessionOptions personal(MenuDefinitionData data, Player player, ApiMenuHandle apiHandle) {
    Objects.requireNonNull(data, "data");
    Player viewer = Objects.requireNonNull(player, "player");
    Location anchor = viewer.getLocation();
    MenuTransform transform = new MenuTransform(
        anchor,
        data.getOffset(),
        anchor.getYaw(),
        0F,
        0F,
        HuiSettings.uiScale()
    );
    return new MenuSessionOptions(
        apiHandle,
        transform,
        true,
        request -> HoloUI.INSTANCE.getSessionManager().navigateSession(viewer, request),
        1F
    );
  }

  public static MenuSessionOptions positioned(MenuTransform transform, MenuNavigator navigator,
                                               float scaleMultiplier) {
    return new MenuSessionOptions(null, transform, false, navigator, scaleMultiplier);
  }
}
