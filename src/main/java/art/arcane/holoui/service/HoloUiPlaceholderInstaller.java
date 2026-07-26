package art.arcane.holoui.service;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;

import java.util.logging.Logger;

public final class HoloUiPlaceholderInstaller {
  private HoloUiPlaceholderInstaller() {
  }

  public static void install(PlaceholderRegistration registration, PlayerSnapshotStore<String> openMenus, Logger logger) {
    registration.register(() -> new HoloUiPlaceholderExpansion(openMenus, logger));
  }
}
