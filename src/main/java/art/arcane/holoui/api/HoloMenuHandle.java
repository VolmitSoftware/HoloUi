package art.arcane.holoui.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;

public interface HoloMenuHandle {
  UUID sessionId();

  UUID playerId();

  String menuId();

  HoloMenuState state();

  boolean setText(String componentId, String miniMessage);

  boolean setItem(String componentId, ItemStack stack);

  boolean setIcon(String componentId, HoloIcon icon);

  void close();

  HoloMenuHandle onClosed(Consumer<HoloCloseReason> callback);
}
