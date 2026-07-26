package art.arcane.holoui.api.internal;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.HoloUiMenuClickEvent;
import art.arcane.holoui.api.HoloUiMenuOpenEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

public final class ApiEvents {

  private ApiEvents() {
  }

  public static boolean fireOpen(Player player, String menuId, String ownerPluginName) {
    if (HoloUiMenuOpenEvent.getHandlerList().getRegisteredListeners().length == 0) {
      return true;
    }

    return !cancelled(new HoloUiMenuOpenEvent(player, menuId, ownerPluginName));
  }

  public static boolean fireClick(Player player, String menuId, String componentId, String ownerPluginName) {
    if (HoloUiMenuClickEvent.getHandlerList().getRegisteredListeners().length == 0) {
      return true;
    }

    return !cancelled(new HoloUiMenuClickEvent(player, menuId, componentId, ownerPluginName));
  }

  private static <T extends Event & Cancellable> boolean cancelled(T event) {
    try {
      Bukkit.getPluginManager().callEvent(event);
    } catch (Throwable error) {
      HoloUI.logExceptionStack(false, error, "Failed to dispatch %s.", event.getEventName());
      return false;
    }

    return event.isCancelled();
  }
}
