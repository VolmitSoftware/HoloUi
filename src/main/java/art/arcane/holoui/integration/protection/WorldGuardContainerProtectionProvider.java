package art.arcane.holoui.integration.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class WorldGuardContainerProtectionProvider implements ContainerProtectionProvider {
  private final Object worldGuardPlugin;
  private final Object protectionQuery;
  private final Object platform;
  private final Object regionQuery;
  private final Object chestAccessFlags;
  private final Method testBlockInteract;
  private final Method wrapPlayer;
  private final Method adaptLocation;
  private final Method testBuild;
  private final Method getConfigManager;
  private final Method getWorldConfig;
  private final Method getLocalPlayerWorld;
  private final Method getSessionManager;
  private final Method hasBypass;
  private final Field useRegions;

  WorldGuardContainerProtectionProvider(Plugin worldGuardPlugin) throws ReflectiveOperationException {
    this.worldGuardPlugin = worldGuardPlugin;
    Class<?> pluginClass = worldGuardPlugin.getClass();
    ClassLoader classLoader = pluginClass.getClassLoader();
    Method createProtectionQuery = pluginClass.getMethod("createProtectionQuery");
    this.protectionQuery = createProtectionQuery.invoke(worldGuardPlugin);
    this.testBlockInteract = protectionQuery.getClass().getMethod("testBlockInteract", Object.class, Block.class);
    this.wrapPlayer = pluginClass.getMethod("wrapPlayer", Player.class);
    this.getConfigManager = pluginClass.getMethod("getConfigManager");

    Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, classLoader);
    Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
    this.platform = worldGuard.getClass().getMethod("getPlatform").invoke(worldGuard);
    Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
    this.regionQuery = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

    Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, classLoader);
    this.adaptLocation = bukkitAdapterClass.getMethod("adapt", Location.class);
    Class<?> worldEditLocationClass = Class.forName("com.sk89q.worldedit.util.Location", true, classLoader);
    Class<?> worldEditWorldClass = Class.forName("com.sk89q.worldedit.world.World", true, classLoader);
    Class<?> localPlayerClass = Class.forName("com.sk89q.worldguard.LocalPlayer", true, classLoader);
    Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, classLoader);
    Class<?> stateFlagArrayClass = Array.newInstance(stateFlagClass, 0).getClass();
    this.testBuild = regionQuery.getClass().getMethod("testBuild", worldEditLocationClass, localPlayerClass, stateFlagArrayClass);
    this.getLocalPlayerWorld = localPlayerClass.getMethod("getWorld");

    Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, classLoader);
    Object chestAccess = flagsClass.getField("CHEST_ACCESS").get(null);
    this.chestAccessFlags = Array.newInstance(stateFlagClass, 1);
    Array.set(chestAccessFlags, 0, chestAccess);

    Object configManager = getConfigManager.invoke(worldGuardPlugin);
    this.getWorldConfig = configManager.getClass().getMethod("get", worldEditWorldClass);
    this.useRegions = getWorldConfig.getReturnType().getField("useRegions");

    Object sessionManager = platform.getClass().getMethod("getSessionManager").invoke(platform);
    this.getSessionManager = platform.getClass().getMethod("getSessionManager");
    this.hasBypass = sessionManager.getClass().getMethod("hasBypass", localPlayerClass, worldEditWorldClass);
  }

  @Override
  public boolean canAccess(Player player, Block block) throws ReflectiveOperationException {
    if (!testBlock(player, block)) {
      return false;
    }
    Block connected = connectedChest(block);
    return connected == null || testBlock(player, connected);
  }

  @Override
  public boolean canAccess(Player player, Entity entity) throws ReflectiveOperationException {
    Object localPlayer = wrapPlayer.invoke(worldGuardPlugin, player);
    Object localPlayerWorld = getLocalPlayerWorld.invoke(localPlayer);
    Object configManager = getConfigManager.invoke(worldGuardPlugin);
    Object worldConfig = getWorldConfig.invoke(configManager, localPlayerWorld);
    if (!useRegions.getBoolean(worldConfig)) {
      return true;
    }
    Object sessionManager = getSessionManager.invoke(platform);
    if ((boolean) hasBypass.invoke(sessionManager, localPlayer, localPlayerWorld)) {
      return true;
    }
    Object adaptedLocation = adaptLocation.invoke(null, entity.getLocation());
    return (boolean) testBuild.invoke(regionQuery, adaptedLocation, localPlayer, chestAccessFlags);
  }

  private boolean testBlock(Player player, Block block) throws ReflectiveOperationException {
    return (boolean) testBlockInteract.invoke(protectionQuery, player, block);
  }

  private Block connectedChest(Block block) {
    if (!(block.getBlockData() instanceof Chest chest) || chest.getType() == Chest.Type.SINGLE) {
      return null;
    }
    BlockFace connectedFace = chest.getType() == Chest.Type.LEFT
        ? clockwise(chest.getFacing())
        : counterClockwise(chest.getFacing());
    Block connected = block.getRelative(connectedFace);
    if (!(connected.getBlockData() instanceof Chest connectedData)
        || connectedData.getFacing() != chest.getFacing()
        || connectedData.getType() == chest.getType()) {
      return null;
    }
    return connected;
  }

  private BlockFace clockwise(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.EAST;
      case EAST -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.WEST;
      case WEST -> BlockFace.NORTH;
      default -> throw new IllegalArgumentException("Unsupported chest facing: " + face);
    };
  }

  private BlockFace counterClockwise(BlockFace face) {
    return switch (face) {
      case NORTH -> BlockFace.WEST;
      case WEST -> BlockFace.SOUTH;
      case SOUTH -> BlockFace.EAST;
      case EAST -> BlockFace.NORTH;
      default -> throw new IllegalArgumentException("Unsupported chest facing: " + face);
    };
  }
}
