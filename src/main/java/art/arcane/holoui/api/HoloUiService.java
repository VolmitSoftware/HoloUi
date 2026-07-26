package art.arcane.holoui.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

public interface HoloUiService {
  HoloMenuHandle open(Plugin owner, Player player, HoloMenu menu);

  HoloMenuHandle open(Plugin owner, Player player, String menuId);

  boolean close(Player player);

  boolean isOpen(Player player);

  Set<String> menuIds();
}
