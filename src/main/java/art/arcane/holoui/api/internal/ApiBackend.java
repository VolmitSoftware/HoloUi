package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.config.MenuDefinitionData;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

interface ApiBackend {

  boolean schedule(Player player, Runnable task, Runnable retired);

  boolean hasSession(Player player);

  boolean openSession(Player player, MenuDefinitionData definition, ApiMenuHandle handle);

  boolean closeSession(Player player, HoloCloseReason reason);

  boolean closeSessionOf(Player player, ApiMenuHandle handle, HoloCloseReason reason);

  void forgetClickGuard(String ownerName);

  MenuDefinitionData definition(String menuId);

  Set<String> menuIds();

  Player online(UUID playerId);
}
