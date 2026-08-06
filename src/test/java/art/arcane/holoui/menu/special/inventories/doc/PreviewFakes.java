/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui.menu.special.inventories.doc;

import art.arcane.holoui.menu.special.inventories.doc.PreviewFakeSupport.Calls;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Furnace;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Shelf;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ChiseledBookshelfInventory;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.JukeboxInventory;
import org.bukkit.inventory.ShelfInventory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static art.arcane.holoui.menu.special.inventories.doc.PreviewFakeSupport.identity;
import static art.arcane.holoui.menu.special.inventories.doc.PreviewFakeSupport.proxy;

/**
 * Builder-style {@link Proxy} fakes for the live Bukkit state the preview engine reads. Shared by
 * every {@code doc} test: build a fake, mutate it between assertions (the proxies read the builder
 * fields live), and query {@link BlockFake#calls(String)} to prove snapshot caching.
 *
 * <p>The non-proxyable pieces ({@link ItemStack}, {@link CookingRecipe}) and the shared proxy
 * plumbing live in {@link PreviewFakeSupport}.
 */
public final class PreviewFakes {

  private PreviewFakes() {
  }

  public static ItemStack stack(Material type, int amount) {
    return PreviewFakeSupport.stack(type, amount);
  }

  public static ItemStack stack(String materialName, int amount) {
    return PreviewFakeSupport.stack(Material.valueOf(materialName), amount);
  }

  public static CookingRecipe<?> cookingRecipe(float experience) {
    return PreviewFakeSupport.cookingRecipe(experience);
  }

  public static FurnaceFake furnace() {
    return new FurnaceFake(Material.FURNACE);
  }

  public static FurnaceFake furnace(Material type) {
    return new FurnaceFake(type);
  }

  public static BrewingStandFake brewingStand() {
    return new BrewingStandFake();
  }

  public static ContainerFake chest(int size) {
    return container(Material.CHEST, Chest.class, size);
  }

  /**
   * Any {@link org.bukkit.block.Container} block: the state interface is what a document's
   * variants key off (a {@code Dispenser} grids 3x3, a {@code Hopper} rows), and the material is
   * what the match resolves on.
   */
  public static ContainerFake container(Material type, Class<?> stateType, int size) {
    return new ContainerFake(type, stateType, Inventory.class, size);
  }

  /** A {@code TileStateInventoryHolder} that is NOT a {@code Container}; six slots, like the real block. */
  public static ContainerFake chiseledBookshelf() {
    return new ContainerFake(Material.CHISELED_BOOKSHELF, ChiseledBookshelf.class, ChiseledBookshelfInventory.class, 6);
  }

  /** The other non-{@code Container} inventory holder: a wall shelf. */
  public static ContainerFake shelf(int size) {
    return new ContainerFake(Material.ACACIA_SHELF, Shelf.class, ShelfInventory.class, size);
  }

  public static BeehiveFake beehive() {
    return new BeehiveFake(Material.BEEHIVE);
  }

  public static CauldronFake cauldron(Material type) {
    return new CauldronFake(type);
  }

  public static JukeboxFake jukebox() {
    return new JukeboxFake();
  }

  public static PlainBlockFake block(Material type) {
    return new PlainBlockFake(type);
  }

  public static InventoryFake inventory(int size) {
    return new InventoryFake(size);
  }

  public static PlayerFake player() {
    return new PlayerFake();
  }

  public static EntityFake entity(EntityType type) {
    return new EntityFake(type);
  }

  /**
   * A {@link HopperMinecart}, so {@code forEntity} takes its five-slot row branch instead of the
   * generic nine-wide grid.
   */
  public static EntityFake hopperMinecart() {
    return new EntityFake(EntityType.HOPPER_MINECART).as(HopperMinecart.class);
  }

  /** Shared block/world plumbing: material, game time, custom name, and the call counter. */
  public abstract static class BlockFake<T extends BlockFake<T>> {

    final Calls calls = new Calls();
    final Map<Integer, ItemStack> items = new LinkedHashMap<>();
    Material type;
    long gameTime;
    String customName;

    // Proxies read the builder fields live, so one instance each is enough; caching also keeps
    // the fakes free of the garbage a per-call proxy would create on every snapshot.
    private World world;
    private BlockState state;
    private BlockData blockData;
    private Inventory inventory;

    BlockFake(Material type) {
      this.type = type;
    }

    @SuppressWarnings("unchecked")
    final T self() {
      return (T) this;
    }

    public T type(Material value) {
      this.type = value;
      return self();
    }

    public T gameTime(long value) {
      this.gameTime = value;
      return self();
    }

    public T customName(String value) {
      this.customName = value;
      return self();
    }

    public T item(int slot, ItemStack stack) {
      items.put(slot, stack);
      return self();
    }

    public int calls(String methodName) {
      return calls.of(methodName);
    }

    public void resetCalls() {
      calls.reset();
    }

    public Block build() {
      return (Block) proxy(Block.class, (proxy, method, arguments) -> {
        calls.record(method.getName());
        return switch (method.getName()) {
          case "getType" -> type;
          case "getState" -> state();
          case "getWorld" -> world();
          case "getBlockData" -> blockData();
          case "getX", "getY", "getZ" -> 0;
          default -> identity(proxy, method, arguments);
        };
      });
    }

    public World world() {
      if (world == null) {
        world = (World) proxy(World.class, (proxy, method, arguments) -> {
          calls.record(method.getName());
          return switch (method.getName()) {
            case "getGameTime", "getFullTime" -> gameTime;
            case "getName" -> "fake";
            default -> identity(proxy, method, arguments);
          };
        });
      }
      return world;
    }

    /** Answers the block-state calls specific to this fake; the shared names are handled already. */
    Object stateCall(Object proxy, Method method, Object[] arguments) {
      return identity(proxy, method, arguments);
    }

    /** The interfaces the block state proxy implements (e.g. {@code Furnace}). */
    abstract Class<?>[] stateTypes();

    BlockData newBlockData() {
      return null;
    }

    Inventory newInventory() {
      return null;
    }

    public BlockData blockData() {
      if (blockData == null) {
        blockData = newBlockData();
      }
      return blockData;
    }

    public Inventory inventoryFake() {
      if (inventory == null) {
        inventory = newInventory();
      }
      return inventory;
    }

    public BlockState state() {
      if (state == null) {
        state = (BlockState) proxy(stateTypes(), (proxy, method, arguments) -> {
          calls.record(method.getName());
          return switch (method.getName()) {
            case "getWorld" -> world();
            case "getType" -> type;
            case "getBlockData" -> blockData();
            case "getCustomName" -> customName;
            case "getInventory", "getSnapshotInventory" -> inventoryFake();
            default -> stateCall(proxy, method, arguments);
          };
        });
      }
      return state;
    }

    final Object inventoryCall(Object proxy, Method method, Object[] arguments, int size) {
      calls.record(method.getName());
      return switch (method.getName()) {
        case "getSize", "getMaxStackSize" -> size;
        case "getItem" -> items.get((Integer) arguments[0]);
        case "getHolder" -> state();
        default -> identity(proxy, method, arguments);
      };
    }
  }

  /** A block with no tile state beyond the material (cauldrons, plain blocks). */
  public static final class PlainBlockFake extends BlockFake<PlainBlockFake> {

    PlainBlockFake(Material type) {
      super(type);
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{BlockState.class};
    }
  }

  public static final class FurnaceFake extends BlockFake<FurnaceFake> {

    private int cookTime;
    private int cookTimeTotal;
    private int burnTime;
    private Map<CookingRecipe<?>, Integer> recipesUsed = Map.of();

    FurnaceFake(Material type) {
      super(type);
    }

    public FurnaceFake cookTime(int value) {
      this.cookTime = value;
      return this;
    }

    public FurnaceFake cookTimeTotal(int value) {
      this.cookTimeTotal = value;
      return this;
    }

    public FurnaceFake burnTime(int value) {
      this.burnTime = value;
      return this;
    }

    public FurnaceFake recipesUsed(Map<CookingRecipe<?>, Integer> value) {
      this.recipesUsed = value;
      return this;
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{Furnace.class};
    }

    @Override
    Object stateCall(Object proxy, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "getCookTime" -> (short) cookTime;
        case "getBurnTime" -> (short) burnTime;
        case "getCookTimeTotal" -> cookTimeTotal;
        case "getRecipesUsed" -> recipesUsed;
        default -> identity(proxy, method, arguments);
      };
    }

    @Override
    Inventory newInventory() {
      return (Inventory) proxy(
          new Class<?>[]{FurnaceInventory.class},
          (proxy, method, arguments) -> switch (method.getName()) {
            case "getSmelting" -> items.get(0);
            case "getFuel" -> items.get(1);
            case "getResult" -> items.get(2);
            default -> inventoryCall(proxy, method, arguments, 3);
          }
      );
    }
  }

  public static final class BrewingStandFake extends BlockFake<BrewingStandFake> {

    private int brewingTime;
    private int fuelLevel;

    BrewingStandFake() {
      super(Material.BREWING_STAND);
    }

    public BrewingStandFake brewingTime(int value) {
      this.brewingTime = value;
      return this;
    }

    public BrewingStandFake fuelLevel(int value) {
      this.fuelLevel = value;
      return this;
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{BrewingStand.class};
    }

    @Override
    Object stateCall(Object proxy, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "getBrewingTime" -> brewingTime;
        case "getFuelLevel" -> fuelLevel;
        default -> identity(proxy, method, arguments);
      };
    }

    @Override
    Inventory newInventory() {
      return (Inventory) proxy(
          new Class<?>[]{BrewerInventory.class},
          (proxy, method, arguments) -> switch (method.getName()) {
            case "getIngredient" -> items.get(3);
            case "getFuel" -> items.get(4);
            default -> inventoryCall(proxy, method, arguments, 5);
          }
      );
    }
  }

  /**
   * Any block state that carries a plain inventory. The state and inventory interfaces are
   * parameters because the previewable holders do not share one supertype below
   * {@code InventoryHolder}: chests are {@code Container}, chiseled bookshelves and shelves are
   * {@code TileStateInventoryHolder}. The inventory proxy implements the state's own covariant
   * inventory type so {@code getInventory()} satisfies the declared return type either way.
   */
  public static final class ContainerFake extends BlockFake<ContainerFake> {

    private final Class<?> stateType;
    private final Class<?> inventoryType;
    private final int size;

    ContainerFake(Material type, Class<?> stateType, Class<?> inventoryType, int size) {
      super(type);
      this.stateType = stateType;
      this.inventoryType = inventoryType;
      this.size = size;
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{stateType};
    }

    @Override
    Inventory newInventory() {
      return (Inventory) proxy(
          new Class<?>[]{inventoryType},
          (proxy, method, arguments) -> inventoryCall(proxy, method, arguments, size)
      );
    }
  }

  public static final class BeehiveFake extends BlockFake<BeehiveFake> {

    private int bees;
    private int maxBees = 3;
    private int honey;
    private int maxHoney = 5;

    BeehiveFake(Material type) {
      super(type);
    }

    public BeehiveFake bees(int value) {
      this.bees = value;
      return this;
    }

    public BeehiveFake maxBees(int value) {
      this.maxBees = value;
      return this;
    }

    public BeehiveFake honey(int value) {
      this.honey = value;
      return this;
    }

    public BeehiveFake maxHoney(int value) {
      this.maxHoney = value;
      return this;
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{org.bukkit.block.Beehive.class};
    }

    @Override
    Object stateCall(Object proxy, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "getEntityCount" -> bees;
        case "getMaxEntities" -> maxBees;
        default -> identity(proxy, method, arguments);
      };
    }

    @Override
    BlockData newBlockData() {
      return (BlockData) proxy(
          new Class<?>[]{org.bukkit.block.data.type.Beehive.class},
          (proxy, method, arguments) -> {
            calls.record(method.getName());
            return switch (method.getName()) {
              case "getHoneyLevel" -> honey;
              case "getMaximumHoneyLevel" -> maxHoney;
              case "getMaterial" -> type;
              default -> identity(proxy, method, arguments);
            };
          }
      );
    }
  }

  public static final class CauldronFake extends BlockFake<CauldronFake> {

    private int level;
    private int maximumLevel = 3;
    private boolean levelled = true;

    CauldronFake(Material type) {
      super(type);
    }

    public CauldronFake level(int value) {
      this.level = value;
      return this;
    }

    public CauldronFake maximumLevel(int value) {
      this.maximumLevel = value;
      return this;
    }

    /** Drops the {@link Levelled} data so the adapter takes its non-levelled fallback branch. */
    public CauldronFake withoutLevelData() {
      this.levelled = false;
      return this;
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{BlockState.class};
    }

    @Override
    BlockData newBlockData() {
      if (!levelled) {
        return (BlockData) proxy(
            new Class<?>[]{BlockData.class},
            (proxy, method, arguments) -> {
              calls.record(method.getName());
              return method.getName().equals("getMaterial") ? type : identity(proxy, method, arguments);
            }
        );
      }
      return (BlockData) proxy(new Class<?>[]{Levelled.class}, (proxy, method, arguments) -> {
        calls.record(method.getName());
        return switch (method.getName()) {
          case "getLevel" -> level;
          case "getMaximumLevel" -> maximumLevel;
          case "getMinimumLevel" -> 0;
          case "getMaterial" -> type;
          default -> identity(proxy, method, arguments);
        };
      });
    }
  }

  public static final class JukeboxFake extends BlockFake<JukeboxFake> {

    private boolean playing;

    JukeboxFake() {
      super(Material.JUKEBOX);
    }

    public JukeboxFake record(ItemStack stack) {
      items.put(0, stack);
      return this;
    }

    public JukeboxFake playing(boolean value) {
      this.playing = value;
      return this;
    }

    @Override
    Class<?>[] stateTypes() {
      return new Class<?>[]{Jukebox.class};
    }

    @Override
    Object stateCall(Object proxy, Method method, Object[] arguments) {
      return switch (method.getName()) {
        case "hasRecord" -> items.get(0) != null;
        case "getRecord" -> items.get(0);
        case "isPlaying" -> playing;
        default -> identity(proxy, method, arguments);
      };
    }

    @Override
    Inventory newInventory() {
      return (Inventory) proxy(
          new Class<?>[]{JukeboxInventory.class},
          (proxy, method, arguments) -> inventoryCall(proxy, method, arguments, 1)
      );
    }
  }

  // ---------------------------------------------------------------------
  // Standalone inventory, player, entity
  // ---------------------------------------------------------------------

  /** A holder-less inventory, e.g. the ender chest a preview context is built directly from. */
  public static final class InventoryFake {

    private final Calls calls = new Calls();
    private final Map<Integer, ItemStack> items = new LinkedHashMap<>();
    private final int size;

    InventoryFake(int size) {
      this.size = size;
    }

    public InventoryFake item(int slot, ItemStack stack) {
      items.put(slot, stack);
      return this;
    }

    public int calls(String methodName) {
      return calls.of(methodName);
    }

    public Inventory build() {
      return (Inventory) proxy(Inventory.class, (proxy, method, arguments) -> {
        calls.record(method.getName());
        return switch (method.getName()) {
          case "getSize", "getMaxStackSize" -> size;
          case "getItem" -> items.get((Integer) arguments[0]);
          case "getHolder" -> null;
          default -> identity(proxy, method, arguments);
        };
      });
    }
  }

  public static final class PlayerFake {

    private Inventory enderChest;

    public PlayerFake enderChest(Inventory value) {
      this.enderChest = value;
      return this;
    }

    public Player build() {
      return (Player) proxy(Player.class, (proxy, method, arguments) -> switch (method.getName()) {
        case "getEnderChest" -> enderChest;
        case "getName" -> "fakePlayer";
        default -> identity(proxy, method, arguments);
      });
    }
  }

  public static final class EntityFake {

    private final Calls calls = new Calls();
    private final EntityType type;
    private Class<?> entityType = Entity.class;
    private Inventory inventory;
    private long gameTime;
    private String customName;

    EntityFake(EntityType type) {
      this.type = type;
    }

    public EntityFake customName(String value) {
      this.customName = value;
      return this;
    }

    /** Narrows the proxy to a concrete entity interface, e.g. {@link HopperMinecart}. */
    public EntityFake as(Class<?> value) {
      this.entityType = value;
      return this;
    }

    public EntityFake inventory(Inventory value) {
      this.inventory = value;
      return this;
    }

    public EntityFake gameTime(long value) {
      this.gameTime = value;
      return this;
    }

    public int calls(String methodName) {
      return calls.of(methodName);
    }

    public Entity build() {
      Class<?>[] types = inventory == null || InventoryHolder.class.isAssignableFrom(entityType)
          ? new Class<?>[]{entityType}
          : new Class<?>[]{entityType, InventoryHolder.class};
      return (Entity) proxy(types, (proxy, method, arguments) -> {
        calls.record(method.getName());
        return switch (method.getName()) {
          case "getType" -> type;
          case "getInventory" -> inventory;
          case "getWorld" -> world();
          case "getCustomName" -> customName;
          default -> identity(proxy, method, arguments);
        };
      });
    }

    private World world() {
      return (World) proxy(World.class, (proxy, method, arguments) -> {
        calls.record(method.getName());
        return switch (method.getName()) {
          case "getGameTime", "getFullTime" -> gameTime;
          case "getName" -> "fake";
          default -> identity(proxy, method, arguments);
        };
      });
    }
  }

}
