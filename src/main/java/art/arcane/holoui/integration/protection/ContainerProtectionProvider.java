package art.arcane.holoui.integration.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

interface ContainerProtectionProvider {
  boolean canAccess(Player player, Block block) throws ReflectiveOperationException;

  boolean canAccess(Player player, Entity entity) throws ReflectiveOperationException;
}
