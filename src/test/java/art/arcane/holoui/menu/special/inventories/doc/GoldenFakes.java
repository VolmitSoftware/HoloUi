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

import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The exact fake block, entity, and inventory state every golden snapshot was captured from.
 *
 * <p>The snapshots in {@code src/test/resources/golden} were captured from these fakes;
 * {@link GoldenEquivalenceTest} rebuilds them through the shipped JSON documents. Both sides read
 * the same state by construction rather than through two copies of the same builder calls that
 * could drift apart — which would turn the gate into a comparison of two different scenarios.
 *
 * <p>Game time is pinned at {@link #GAME_TIME} everywhere. The only state that depends on it is the
 * surge detector, and the one scenario that needs a surge drives it explicitly; see
 * {@link #SURGE_WINDOW_TICKS} and {@link #surgingFurnace()}.
 */
public final class GoldenFakes {

  public static final long GAME_TIME = 1234L;

  /** Ticks between the priming sample and the real one in the surge scenario. */
  public static final int SURGE_WINDOW_TICKS = 30;

  /** Cook counter the surge scenario advances to, 80 ticks up in {@link #SURGE_WINDOW_TICKS}. */
  public static final int SURGE_COOK_TIME = 180;

  private GoldenFakes() {
  }

  /**
   * Any {@code Container} block: the state interface decides the layout branch (a {@code Dispenser}
   * grids 3x3, a {@code Hopper} rows), the material decides the theme.
   */
  public static PreviewFakes.ContainerFake storage(Material type, Class<?> stateType, int size) {
    return PreviewFakes.container(type, stateType, size)
        .gameTime(GAME_TIME)
        .item(0, PreviewFakes.stack(Material.DIAMOND, 1))
        .item(1, PreviewFakes.stack(Material.STONE, 32))
        .item(2, PreviewFakes.stack(Material.BOOK, 3));
  }

  public static PreviewFakes.ContainerFake chest(int size) {
    return storage(Material.CHEST, Chest.class, size);
  }

  public static PreviewFakes.ContainerFake trappedChest() {
    return storage(Material.TRAPPED_CHEST, Chest.class, 27);
  }

  public static PreviewFakes.ContainerFake barrel() {
    return storage(Material.BARREL, Barrel.class, 27);
  }

  public static PreviewFakes.ContainerFake copperChest() {
    return storage(Material.COPPER_CHEST, Chest.class, 27);
  }

  public static PreviewFakes.ContainerFake shulkerBox(Material type) {
    return storage(type, ShulkerBox.class, 27);
  }

  public static PreviewFakes.ContainerFake dispenser() {
    return storage(Material.DISPENSER, Dispenser.class, 9);
  }

  public static PreviewFakes.ContainerFake hopper() {
    return storage(Material.HOPPER, Hopper.class, 5);
  }

  /** The sixteen dyed boxes plus the undyed one, in enum order; the legacy aliases are skipped. */
  public static List<Material> shulkerBoxes() {
    List<Material> materials = new ArrayList<>();
    for (Material material : Material.values()) {
      if (!material.isLegacy() && material.name().endsWith("SHULKER_BOX")) {
        materials.add(material);
      }
    }
    return materials;
  }

  public static PreviewFakes.ContainerFake shelf() {
    return PreviewFakes.shelf(3)
        .gameTime(GAME_TIME)
        .item(0, PreviewFakes.stack(Material.BOOK, 1));
  }

  public static PreviewFakes.ContainerFake chiseledBookshelf() {
    return PreviewFakes.chiseledBookshelf()
        .gameTime(GAME_TIME)
        .item(0, PreviewFakes.stack(Material.BOOK, 1))
        .item(3, PreviewFakes.stack(Material.BOOK, 2));
  }

  public static PreviewFakes.FurnaceFake furnaceIdle() {
    return PreviewFakes.furnace(Material.FURNACE).gameTime(GAME_TIME);
  }

  /** Recipe experience 0.5 used 3 times: banked XP is exactly 1.5, with no float slop. */
  public static PreviewFakes.FurnaceFake smelting(Material type) {
    return PreviewFakes.furnace(type)
        .cookTime(100)
        .cookTimeTotal(200)
        .burnTime(300)
        .recipesUsed(Map.<CookingRecipe<?>, Integer>of(PreviewFakes.cookingRecipe(0.5f), 3))
        .gameTime(GAME_TIME)
        .item(0, PreviewFakes.stack(Material.IRON_ORE, 1))
        .item(1, PreviewFakes.stack(Material.COAL, 8))
        .item(2, PreviewFakes.stack(Material.IRON_INGOT, 2));
  }

  /** The same furnace, rewound to the tick the surge detector is primed at. */
  public static PreviewFakes.FurnaceFake surgingFurnace() {
    return smelting(Material.FURNACE).gameTime(GAME_TIME - SURGE_WINDOW_TICKS);
  }

  public static PreviewFakes.BrewingStandFake brewingIdle() {
    return PreviewFakes.brewingStand().gameTime(GAME_TIME);
  }

  public static PreviewFakes.BrewingStandFake brewingActive() {
    return PreviewFakes.brewingStand()
        .brewingTime(200)
        .fuelLevel(10)
        .gameTime(GAME_TIME)
        .item(0, PreviewFakes.stack(Material.POTION, 1))
        .item(1, PreviewFakes.stack(Material.POTION, 1))
        .item(3, PreviewFakes.stack(Material.NETHER_WART, 1))
        .item(4, PreviewFakes.stack(Material.BLAZE_POWDER, 4));
  }

  public static PreviewFakes.JukeboxFake jukeboxEmpty() {
    return PreviewFakes.jukebox().gameTime(GAME_TIME);
  }

  public static PreviewFakes.JukeboxFake jukeboxPlaying() {
    return PreviewFakes.jukebox()
        .record(PreviewFakes.stack(Material.MUSIC_DISC_CAT, 1))
        .playing(true)
        .gameTime(GAME_TIME);
  }

  public static PreviewFakes.BeehiveFake beehive() {
    return PreviewFakes.beehive()
        .bees(2)
        .maxBees(3)
        .honey(3)
        .maxHoney(5)
        .gameTime(GAME_TIME);
  }

  public static PreviewFakes.CauldronFake cauldronEmpty() {
    return PreviewFakes.cauldron(Material.CAULDRON).gameTime(GAME_TIME);
  }

  public static PreviewFakes.CauldronFake cauldronWater() {
    return PreviewFakes.cauldron(Material.WATER_CAULDRON)
        .level(2)
        .maximumLevel(3)
        .gameTime(GAME_TIME);
  }

  /** Lava cauldrons carry no level property in vanilla, so the layout takes its non-Levelled path. */
  public static PreviewFakes.CauldronFake cauldronLava() {
    return PreviewFakes.cauldron(Material.LAVA_CAULDRON)
        .withoutLevelData()
        .gameTime(GAME_TIME);
  }

  public static PreviewFakes.CauldronFake cauldronPowderSnow() {
    return PreviewFakes.cauldron(Material.POWDER_SNOW_CAULDRON)
        .level(3)
        .maximumLevel(3)
        .gameTime(GAME_TIME);
  }

  public static Inventory minecartInventory() {
    return PreviewFakes.inventory(5)
        .item(0, PreviewFakes.stack(Material.IRON_INGOT, 4))
        .item(2, PreviewFakes.stack(Material.STONE, 1))
        .build();
  }

  public static PreviewFakes.EntityFake hopperMinecart(Inventory inventory) {
    return PreviewFakes.hopperMinecart().inventory(inventory).gameTime(GAME_TIME);
  }

  public static Inventory enderChestInventory() {
    return PreviewFakes.inventory(27)
        .item(0, PreviewFakes.stack(Material.DIAMOND, 1))
        .item(8, PreviewFakes.stack(Material.BOOK, 5))
        .build();
  }

  public static PreviewFakes.PlainBlockFake enderChestBlock() {
    return PreviewFakes.block(Material.ENDER_CHEST).gameTime(GAME_TIME);
  }
}
