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

import art.arcane.holoui.expr.ExprFunctions;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.block.Jukebox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Samples live Bukkit container state into the flat variable map a {@link PreviewStateContext}
 * resolves against. Every reading is sampled once per build and handed to the document as a plain
 * value, so an expression never touches a Bukkit object.
 *
 * <p>A context picks one category once, at construction, from the block state / entity / inventory
 * type. {@link #catalog()} is the authoritative list of the variable names each category
 * publishes; the shipped documents and the editor's variable list are kept in sync against it.
 * A context publishes the {@code universal} group always, the {@code inventory} group whenever it
 * has an inventory (including furnaces, brewing stands, and jukeboxes), and the group named by its
 * own category.
 */
final class PreviewStateAdapters {

  static final String CATEGORY_FURNACE = "furnace";
  static final String CATEGORY_BREWING = "brewing";
  static final String CATEGORY_BEEHIVE = "beehive";
  static final String CATEGORY_CAULDRON = "cauldron";
  static final String CATEGORY_JUKEBOX = "jukebox";
  static final String CATEGORY_INVENTORY = "inventory";
  static final String CATEGORY_STATIC = "static";

  private static final String GROUP_UNIVERSAL = "universal";

  // Brewing has no Bukkit-exposed total, and the fuel gauge is fixed at twenty blaze powder.
  private static final int BREW_TOTAL_TICKS = 400;
  private static final int MAX_FUEL_LEVEL = 20;
  private static final int TICKS_PER_SECOND = 20;
  private static final int DEFAULT_MAX_BEES = 3;
  private static final int DEFAULT_MAX_HONEY = 5;
  private static final int CAULDRON_MAX_LEVEL = 3;

  /** Older server APIs have no {@code getRecipesUsed}; the reflective gate keeps them working. */
  private static final boolean RECIPES_USED_AVAILABLE = hasRecipesUsed();

  private PreviewStateAdapters() {
  }

  /** The category a context uses plus the inventory it exposes, resolved in one pass. */
  record Selection(String category, Inventory inventory) {
  }

  static Map<String, Set<String>> catalog() {
    Map<String, Set<String>> catalog = new LinkedHashMap<>();
    catalog.put(GROUP_UNIVERSAL, names("time", "blockType", "customName"));
    catalog.put(CATEGORY_INVENTORY, names("inventory.size", "inventory.occupied"));
    catalog.put(CATEGORY_FURNACE, names(
        "cookTime", "cookTimeTotal", "burnTime", "fuelSeconds", "bankedXp", "lit", "surge.active", "surge.gain"));
    catalog.put(CATEGORY_BREWING, names(
        "brewTime", "brewTotal", "fuelLevel", "maxFuel", "surge.active", "surge.gain"));
    catalog.put(CATEGORY_BEEHIVE, names("bees", "maxBees", "honey", "maxHoney"));
    catalog.put(CATEGORY_CAULDRON, names("level", "maxLevel", "fluid"));
    catalog.put(CATEGORY_JUKEBOX, names("playing", "record"));
    return Collections.unmodifiableMap(catalog);
  }

  /**
   * Every name a provider namespace may not take: each cataloged variable, the first segment of the
   * dotted ones ({@code inventory}, {@code surge}), and {@code vars}. A provider called
   * {@code inventory} would otherwise publish {@code inventory.size} straight over the built-in.
   */
  private static final Set<String> RESERVED_NAMESPACES = reservedNamespaces();

  /** True when a provider using this namespace would shadow a built-in variable. */
  static boolean isReservedNamespace(String namespace) {
    return RESERVED_NAMESPACES.contains(namespace);
  }

  private static Set<String> reservedNamespaces() {
    Set<String> reserved = new LinkedHashSet<>();
    reserved.add("vars");
    for (Set<String> group : catalog().values()) {
      for (String name : group) {
        reserved.add(name);
        int dot = name.indexOf('.');
        if (dot > 0) {
          reserved.add(name.substring(0, dot));
        }
      }
    }
    return Collections.unmodifiableSet(reserved);
  }

  /** True for the categories whose surge variables need a {@link TimeFlowTracker}. */
  static boolean tracksTimeFlow(String category) {
    return category.equals(CATEGORY_FURNACE) || category.equals(CATEGORY_BREWING);
  }

  /** Brewing counts its timer down to zero; furnace cook time counts up. */
  static boolean countsDown(String category) {
    return category.equals(CATEGORY_BREWING);
  }

  // ---------------------------------------------------------------------
  // Category selection
  // ---------------------------------------------------------------------

  /** Dispatch order: the first category a block state satisfies wins. */
  static Selection selectBlock(Block block, Player player) {
    Material type = block.getType();
    if (type == Material.ENDER_CHEST) {
      return new Selection(CATEGORY_INVENTORY, player == null ? null : player.getEnderChest());
    }
    BlockState state = block.getState();
    if (state instanceof BrewingStand stand) {
      return new Selection(CATEGORY_BREWING, stand.getInventory());
    }
    if (state instanceof Furnace furnace) {
      return new Selection(CATEGORY_FURNACE, furnace.getInventory());
    }
    if (state instanceof Container container) {
      return new Selection(CATEGORY_INVENTORY, container.getInventory());
    }
    if (state instanceof Jukebox jukebox) {
      return new Selection(CATEGORY_JUKEBOX, jukebox.getInventory());
    }
    // Chiseled bookshelves and shelves are TileStateInventoryHolder, not Container, so they need
    // this fallback to be previewable at all. It must stay below the Jukebox branch: a jukebox is
    // also a non-Container InventoryHolder and would otherwise be demoted to a plain inventory.
    if (state instanceof InventoryHolder holder) {
      return new Selection(CATEGORY_INVENTORY, holder.getInventory());
    }
    if (type == Material.BEEHIVE || type == Material.BEE_NEST) {
      return new Selection(CATEGORY_BEEHIVE, null);
    }
    if (isCauldron(type)) {
      return new Selection(CATEGORY_CAULDRON, null);
    }
    return new Selection(CATEGORY_STATIC, null);
  }

  static Selection selectEntity(Entity entity) {
    if (entity instanceof InventoryHolder holder) {
      return new Selection(CATEGORY_INVENTORY, holder.getInventory());
    }
    return new Selection(CATEGORY_STATIC, null);
  }

  // ---------------------------------------------------------------------
  // Sampling
  // ---------------------------------------------------------------------

  static void sample(
      String category,
      Block block,
      Entity entity,
      Inventory inventory,
      TimeFlowTracker flow,
      long gameTime,
      Map<String, Object> out
  ) {
    out.put("time", (double) gameTime);
    // The universal group is published unconditionally so every cataloged universal name resolves
    // in every context; a target with no material (a bare ender-chest inventory, or statics) gets
    // the empty string rather than a missing variable, which documents branch on as `blockType != ""`.
    out.put("blockType", blockType(block, entity));
    out.put("customName", customName(block, entity));
    if (inventory != null) {
      sampleInventory(inventory, out);
    }
    switch (category) {
      case CATEGORY_FURNACE -> sampleFurnace(block, flow, gameTime, out);
      case CATEGORY_BREWING -> sampleBrewing(block, flow, gameTime, out);
      case CATEGORY_BEEHIVE -> sampleBeehive(block, out);
      case CATEGORY_CAULDRON -> sampleCauldron(block, out);
      case CATEGORY_JUKEBOX -> sampleJukebox(block, out);
      default -> {
      }
    }
  }

  private static void sampleInventory(Inventory inventory, Map<String, Object> out) {
    int size = inventory.getSize();
    int occupied = 0;
    for (int slot = 0; slot < size; slot++) {
      if (!empty(inventory.getItem(slot))) {
        occupied++;
      }
    }
    out.put("inventory.size", (double) size);
    out.put("inventory.occupied", (double) occupied);
  }

  private static void sampleFurnace(Block block, TimeFlowTracker flow, long gameTime, Map<String, Object> out) {
    if (block == null || !(block.getState() instanceof Furnace furnace)) {
      return;
    }
    int cookTime = furnace.getCookTime();
    flow.sample(gameTime, cookTime);
    int burnTime = furnace.getBurnTime();
    out.put("cookTime", (double) cookTime);
    out.put("cookTimeTotal", (double) furnace.getCookTimeTotal());
    out.put("burnTime", (double) burnTime);
    // Whole seconds, matching the integer division the retired fuel line rendered.
    out.put("fuelSeconds", (double) (burnTime / TICKS_PER_SECOND));
    out.put("bankedXp", bankedXp(furnace));
    out.put("lit", burnTime > 0);
    out.put("surge.active", flow.surging());
    out.put("surge.gain", flow.surgeSeconds());
  }

  private static void sampleBrewing(Block block, TimeFlowTracker flow, long gameTime, Map<String, Object> out) {
    if (block == null || !(block.getState() instanceof BrewingStand stand)) {
      return;
    }
    int brewTime = stand.getBrewingTime();
    flow.sample(gameTime, brewTime);
    out.put("brewTime", (double) brewTime);
    out.put("brewTotal", (double) BREW_TOTAL_TICKS);
    out.put("fuelLevel", (double) stand.getFuelLevel());
    out.put("maxFuel", (double) MAX_FUEL_LEVEL);
    out.put("surge.active", flow.surging());
    out.put("surge.gain", flow.surgeSeconds());
  }

  /** Hive readings, including the defaults used when the tile state is missing. */
  private static void sampleBeehive(Block block, Map<String, Object> out) {
    if (block == null) {
      return;
    }
    int bees = 0;
    int maxBees = DEFAULT_MAX_BEES;
    int honey = 0;
    int maxHoney = DEFAULT_MAX_HONEY;
    BlockState state = block.getState();
    if (state instanceof Beehive beehive) {
      bees = Math.max(0, beehive.getEntityCount());
      maxBees = Math.max(1, beehive.getMaxEntities());
    }
    BlockData data = state.getBlockData();
    if (data instanceof org.bukkit.block.data.type.Beehive beehiveData) {
      honey = beehiveData.getHoneyLevel();
      maxHoney = Math.max(1, beehiveData.getMaximumHoneyLevel());
    }
    out.put("bees", (double) bees);
    out.put("maxBees", (double) maxBees);
    out.put("honey", (double) honey);
    out.put("maxHoney", (double) maxHoney);
  }

  /** Cauldron fill level plus the fluid name the document colours its fill with. */
  private static void sampleCauldron(Block block, Map<String, Object> out) {
    if (block == null) {
      return;
    }
    Material type = block.getType();
    int level;
    int maxLevel;
    if (type == Material.CAULDRON) {
      level = 0;
      maxLevel = CAULDRON_MAX_LEVEL;
    } else if (block.getBlockData() instanceof Levelled levelled) {
      maxLevel = Math.max(1, levelled.getMaximumLevel());
      level = Math.max(0, levelled.getLevel());
    } else {
      level = CAULDRON_MAX_LEVEL;
      maxLevel = CAULDRON_MAX_LEVEL;
    }
    out.put("level", (double) level);
    out.put("maxLevel", (double) maxLevel);
    out.put("fluid", fluid(type));
  }

  private static void sampleJukebox(Block block, Map<String, Object> out) {
    if (block == null || !(block.getState() instanceof Jukebox jukebox)) {
      return;
    }
    boolean hasRecord = jukebox.hasRecord();
    out.put("playing", hasRecord && jukebox.isPlaying());
    // Empty string rather than absent, so documents can branch on `record != ""`.
    out.put("record", hasRecord ? ExprFunctions.readable(jukebox.getRecord().getType().name()) : "");
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  /**
   * Material name for a block, or the material an entity maps to (which is how a minecart names
   * itself). Empty when the context has neither, e.g. a bare ender-chest inventory.
   */
  private static String blockType(Block block, Entity entity) {
    if (block != null) {
      return block.getType().name();
    }
    if (entity != null) {
      return material(entity).name();
    }
    return "";
  }

  /**
   * The name a player gave the container or entity, or the empty string. A whitespace-only name
   * collapses to empty so documents can branch on {@code customName != ''} and fall back to their
   * themed title.
   */
  private static String customName(Block block, Entity entity) {
    if (block != null) {
      return block.getState() instanceof Nameable nameable ? name(nameable.getCustomName()) : "";
    }
    return entity == null ? "" : name(entity.getCustomName());
  }

  private static String name(String value) {
    return value == null || value.isBlank() ? "" : value;
  }

  private static Material material(Entity entity) {
    Material material = Material.matchMaterial(entity.getType().name());
    return material == null ? Material.MINECART : material;
  }

  /** Names the cauldron contents behind the fill colors the retired layout switched on. */
  private static String fluid(Material type) {
    return switch (type) {
      case CAULDRON -> "empty";
      case LAVA_CAULDRON -> "lava";
      case POWDER_SNOW_CAULDRON -> "powder_snow";
      default -> "water";
    };
  }

  /** Total banked experience, or -1 when the running server has no {@code getRecipesUsed}. */
  private static double bankedXp(Furnace furnace) {
    if (!RECIPES_USED_AVAILABLE) {
      return -1;
    }
    double xp = 0;
    for (Map.Entry<CookingRecipe<?>, Integer> entry : furnace.getRecipesUsed().entrySet()) {
      xp += entry.getKey().getExperience() * entry.getValue();
    }
    return xp;
  }

  private static boolean hasRecipesUsed() {
    try {
      Furnace.class.getMethod("getRecipesUsed");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  static boolean empty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1;
  }

  private static boolean isCauldron(Material type) {
    return type == Material.CAULDRON
        || type == Material.WATER_CAULDRON
        || type == Material.LAVA_CAULDRON
        || type == Material.POWDER_SNOW_CAULDRON;
  }

  /** Insertion-ordered and immutable, so a catalog dump is byte-stable across runs. */
  private static Set<String> names(String... values) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(values)));
  }
}
