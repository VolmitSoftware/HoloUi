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

import art.arcane.holoui.api.PreviewStateProvider;
import art.arcane.holoui.api.PreviewStateProviders;
import art.arcane.holoui.expr.ExprEvaluator;
import art.arcane.holoui.expr.ExprException;
import art.arcane.holoui.expr.ExprFunctions;
import art.arcane.holoui.expr.ExprParser;
import art.arcane.volmlib.util.localization.MessageArgs;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PreviewStateContextTest {

  private static final double EPSILON = 1.0E-6D;

  private final List<PreviewStateProvider> registered = new ArrayList<>();

  @After
  public void unregisterProviders() {
    for (PreviewStateProvider provider : registered) {
      PreviewStateProviders.unregister(provider);
    }
    registered.clear();
  }

  // ---------------------------------------------------------------------
  // Furnace
  // ---------------------------------------------------------------------

  @Test
  public void furnaceContextExposesLiveCookState() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace()
        .cookTime(100)
        .cookTimeTotal(200)
        .burnTime(300)
        .gameTime(1234L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("furnace", context.category());
    assertEquals(1234.0D, number(context, "time"), EPSILON);
    assertEquals("FURNACE", context.variable("blockType"));
    assertEquals(100.0D, number(context, "cookTime"), EPSILON);
    assertEquals(200.0D, number(context, "cookTimeTotal"), EPSILON);
    assertEquals(300.0D, number(context, "burnTime"), EPSILON);
    assertEquals(15.0D, number(context, "fuelSeconds"), EPSILON);
    assertEquals(Boolean.TRUE, context.variable("lit"));
    assertEquals(Boolean.FALSE, context.variable("surge.active"));
    assertEquals(0.0D, number(context, "surge.gain"), EPSILON);
    assertEquals(3.0D, number(context, "inventory.size"), EPSILON);
  }

  @Test
  public void unlitFurnaceReportsNoFuelSeconds() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace(Material.BLAST_FURNACE).burnTime(0);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("BLAST_FURNACE", context.variable("blockType"));
    assertEquals(Boolean.FALSE, context.variable("lit"));
    assertEquals(0.0D, number(context, "fuelSeconds"), EPSILON);
  }

  @Test
  public void bankedXpSumsTheExperienceOfEveryUsedRecipe() {
    CookingRecipe<?> first = PreviewFakes.cookingRecipe(0.7F);
    CookingRecipe<?> second = PreviewFakes.cookingRecipe(0.2F);
    Map<CookingRecipe<?>, Integer> used = new LinkedHashMap<>();
    used.put(first, 2);
    used.put(second, 1);
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().recipesUsed(used);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(1.6D, number(context, "bankedXp"), 1.0E-5D);
  }

  @Test
  public void surgeActivatesWhenCookTimeOutrunsGameTime() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().cookTime(10).burnTime(200).gameTime(100L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(Boolean.FALSE, context.variable("surge.active"));

    fake.gameTime(101L).cookTime(200);
    assertEquals(Boolean.TRUE, context.variable("surge.active"));
    assertEquals(9.45D, number(context, "surge.gain"), EPSILON);
  }

  // ---------------------------------------------------------------------
  // Other categories
  // ---------------------------------------------------------------------

  @Test
  public void brewingContextExposesBrewProgressAndFuel() {
    PreviewFakes.BrewingStandFake fake = PreviewFakes.brewingStand()
        .brewingTime(120)
        .fuelLevel(8)
        .gameTime(50L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("brewing", context.category());
    assertEquals(120.0D, number(context, "brewTime"), EPSILON);
    assertEquals(400.0D, number(context, "brewTotal"), EPSILON);
    assertEquals(8.0D, number(context, "fuelLevel"), EPSILON);
    assertEquals(20.0D, number(context, "maxFuel"), EPSILON);
    assertEquals(Boolean.FALSE, context.variable("surge.active"));
    assertEquals(5.0D, number(context, "inventory.size"), EPSILON);
  }

  @Test
  public void brewingSurgeCountsTheBrewTimerDownwards() {
    PreviewFakes.BrewingStandFake fake = PreviewFakes.brewingStand().brewingTime(300).gameTime(10L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(Boolean.FALSE, context.variable("surge.active"));

    fake.gameTime(11L).brewingTime(200);
    assertEquals(Boolean.TRUE, context.variable("surge.active"));
    assertEquals(4.95D, number(context, "surge.gain"), EPSILON);
  }

  @Test
  public void beehiveContextExposesBeesAndHoney() {
    PreviewFakes.BeehiveFake fake = PreviewFakes.beehive().bees(2).maxBees(3).honey(4).maxHoney(5);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("beehive", context.category());
    assertEquals(2.0D, number(context, "bees"), EPSILON);
    assertEquals(3.0D, number(context, "maxBees"), EPSILON);
    assertEquals(4.0D, number(context, "honey"), EPSILON);
    assertEquals(5.0D, number(context, "maxHoney"), EPSILON);
    assertNull(context.inventory());
  }

  @Test
  public void cauldronContextExposesLevelAndFluid() {
    PreviewFakes.CauldronFake water = PreviewFakes.cauldron(Material.WATER_CAULDRON).level(2).maximumLevel(3);
    PreviewStateContext context = PreviewStateContext.forBlock(water.build(), null, Map.of());

    assertEquals("cauldron", context.category());
    assertEquals(2.0D, number(context, "level"), EPSILON);
    assertEquals(3.0D, number(context, "maxLevel"), EPSILON);
    assertEquals("water", context.variable("fluid"));
  }

  @Test
  public void emptyCauldronReportsNoLevelWithoutReadingBlockData() {
    PreviewFakes.CauldronFake empty = PreviewFakes.cauldron(Material.CAULDRON).level(3);
    PreviewStateContext context = PreviewStateContext.forBlock(empty.build(), null, Map.of());

    assertEquals(0.0D, number(context, "level"), EPSILON);
    assertEquals(3.0D, number(context, "maxLevel"), EPSILON);
    assertEquals("empty", context.variable("fluid"));
    assertEquals(0, empty.calls("getLevel"));
  }

  @Test
  public void lavaCauldronNamesItsFluid() {
    PreviewFakes.CauldronFake lava = PreviewFakes.cauldron(Material.LAVA_CAULDRON).level(3).maximumLevel(3);
    PreviewStateContext context = PreviewStateContext.forBlock(lava.build(), null, Map.of());

    assertEquals("lava", context.variable("fluid"));
  }

  @Test
  public void jukeboxContextExposesPlayingAndRecord() {
    PreviewFakes.JukeboxFake fake = PreviewFakes.jukebox()
        .record(PreviewFakes.stack(Material.MUSIC_DISC_CAT, 1))
        .playing(true);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("jukebox", context.category());
    assertEquals(Boolean.TRUE, context.variable("playing"));
    assertEquals("Music Disc Cat", context.variable("record"));
    assertEquals(1.0D, number(context, "inventory.size"), EPSILON);
  }

  @Test
  public void emptyJukeboxReportsNoRecord() {
    PreviewStateContext context = PreviewStateContext.forBlock(PreviewFakes.jukebox().build(), null, Map.of());

    assertEquals(Boolean.FALSE, context.variable("playing"));
    assertEquals("", context.variable("record"));
  }

  @Test
  public void chestContextCountsOccupiedSlots() {
    PreviewFakes.ContainerFake fake = PreviewFakes.chest(27)
        .item(0, PreviewFakes.stack(Material.COAL, 3))
        .item(5, PreviewFakes.stack(Material.STONE, 1));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("inventory", context.category());
    assertEquals(27.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(2.0D, number(context, "inventory.occupied"), EPSILON);
  }

  @Test
  public void chiseledBookshelfIsPreviewableEvenThoughItIsNotAContainer() {
    PreviewFakes.ContainerFake fake = PreviewFakes.chiseledBookshelf()
        .item(0, PreviewFakes.stack(Material.BOOK, 1))
        .item(4, PreviewFakes.stack(Material.BOOK, 1));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("inventory", context.category());
    assertEquals(fake.inventoryFake(), context.inventory());
    assertEquals(6.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(2.0D, number(context, "inventory.occupied"), EPSILON);
    assertEquals(Boolean.TRUE, context.call("occupied", List.of(4.0D)));
    assertEquals(1.0D, (Double) context.call("count", List.of(0.0D)), EPSILON);
    assertEquals(Boolean.FALSE, context.call("occupied", List.of(5.0D)));
  }

  @Test
  public void shelfIsPreviewableEvenThoughItIsNotAContainer() {
    PreviewFakes.ContainerFake fake = PreviewFakes.shelf(3).item(2, PreviewFakes.stack(Material.STONE, 1));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("inventory", context.category());
    assertEquals(3.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(1.0D, number(context, "inventory.occupied"), EPSILON);
  }

  @Test
  public void jukeboxKeepsItsOwnCategoryAheadOfTheInventoryHolderFallback() {
    PreviewFakes.JukeboxFake fake = PreviewFakes.jukebox().record(PreviewFakes.stack(Material.MUSIC_DISC_CAT, 1));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("jukebox", context.category());
    assertEquals("Music Disc Cat", context.variable("record"));
  }

  @Test
  public void plainBlockHasNoCategoryState() {
    PreviewStateContext context = PreviewStateContext.forBlock(PreviewFakes.block(Material.STONE).build(), null, Map.of());

    assertEquals("static", context.category());
    assertEquals("STONE", context.variable("blockType"));
    assertNull(context.inventory());
    assertNull(context.variable("cookTime"));
  }

  @Test
  public void enderChestBlockUsesThePlayerEnderChest() {
    Inventory enderChest = PreviewFakes.inventory(27).item(0, PreviewFakes.stack(Material.DIAMOND, 1)).build();
    Player player = PreviewFakes.player().enderChest(enderChest).build();
    Block block = PreviewFakes.block(Material.ENDER_CHEST).build();
    PreviewStateContext context = PreviewStateContext.forBlock(block, player, Map.of());

    assertEquals("inventory", context.category());
    assertEquals(enderChest, context.inventory());
    assertEquals(27.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(1.0D, number(context, "inventory.occupied"), EPSILON);
  }

  @Test
  public void entityContextUsesTheHolderInventory() {
    Inventory cart = PreviewFakes.inventory(5).item(1, PreviewFakes.stack(Material.STONE, 2)).build();
    Entity entity = PreviewFakes.entity(EntityType.HOPPER_MINECART).inventory(cart).gameTime(7L).build();
    PreviewStateContext context = PreviewStateContext.forEntity(entity, null, Map.of());

    assertEquals("inventory", context.category());
    assertEquals("HOPPER_MINECART", context.variable("blockType"));
    assertEquals(7.0D, number(context, "time"), EPSILON);
    assertEquals(5.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(1.0D, number(context, "inventory.occupied"), EPSILON);
  }

  @Test
  public void inventoryContextExposesSizeAndOccupiedWithoutABlock() {
    Inventory chest = PreviewFakes.inventory(9).item(8, PreviewFakes.stack(Material.STONE, 1)).build();
    PreviewStateContext context = PreviewStateContext.forInventory(chest, Map.of());

    assertEquals("inventory", context.category());
    assertEquals(9.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(1.0D, number(context, "inventory.occupied"), EPSILON);
    // The universal group is published in every context; a target with no material reads as empty.
    assertEquals("", context.variable("blockType"));
  }

  @Test
  public void staticsContextExposesOnlyVarsAndTime() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of("accent", 16711680.0D));

    assertEquals("static", context.category());
    assertNull(context.inventory());
    assertEquals(16711680.0D, number(context, "vars.accent"), EPSILON);
    assertTrue(context.variable("time") instanceof Double);
    assertEquals("", context.variable("blockType"));
    assertNull(context.variable("inventory.size"));
  }

  // ---------------------------------------------------------------------
  // Variable resolution
  // ---------------------------------------------------------------------

  @Test
  public void varsAreOnlyReachableUnderTheVarsPrefix() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().cookTime(100);
    PreviewStateContext context = PreviewStateContext.forBlock(
        fake.build(),
        null,
        Map.of("accent", 255.0D, "cookTime", 7.0D)
    );

    assertEquals(255.0D, number(context, "vars.accent"), EPSILON);
    assertNull(context.variable("accent"));
    assertEquals(7.0D, number(context, "vars.cookTime"), EPSILON);
    assertEquals(100.0D, number(context, "cookTime"), EPSILON);
  }

  @Test
  public void unknownVariableThrowsNamingTheVariable() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    try {
      ExprEvaluator.eval(ExprParser.parse("noSuchThing"), context);
      fail("expected an unknown variable failure");
    } catch (ExprException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("noSuchThing"));
    }
  }

  @Test
  public void providerNamespacesMergeIntoVariables() {
    register(provider("test", Map.of("foo", 5, "flag", Boolean.TRUE, "label", "hi")));
    PreviewStateContext context = PreviewStateContext.forBlock(PreviewFakes.chest(9).build(), null, Map.of());

    assertEquals(5.0D, number(context, "test.foo"), EPSILON);
    assertEquals(Boolean.TRUE, context.variable("test.flag"));
    assertEquals("hi", context.variable("test.label"));
  }

  @Test
  public void unregisteredProviderStopsContributingVariables() {
    PreviewStateProvider provider = provider("test", Map.of("foo", 5));
    PreviewStateProviders.register(provider);
    PreviewStateProviders.unregister(provider);
    PreviewStateContext context = PreviewStateContext.forBlock(PreviewFakes.chest(9).build(), null, Map.of());

    assertNull(context.variable("test.foo"));
  }

  /** A provider must not be able to publish over a built-in: it is dropped whole, not merged. */
  @Test
  public void providersCannotShadowBuiltInNamespaces() {
    register(provider("inventory", Map.of("size", 999, "occupied", 999)));
    register(provider("surge", Map.of("active", Boolean.TRUE, "gain", 99)));
    register(provider("vars", Map.of("accent", 1)));
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().burnTime(0).gameTime(4L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of("accent", 7.0D));

    assertEquals(3.0D, number(context, "inventory.size"), EPSILON);
    assertEquals(0.0D, number(context, "inventory.occupied"), EPSILON);
    assertEquals(Boolean.FALSE, context.variable("surge.active"));
    assertEquals(0.0D, number(context, "surge.gain"), EPSILON);
    assertEquals(7.0D, number(context, "vars.accent"), EPSILON);
  }

  @Test
  public void reservedNamespacesCoverEveryCatalogedNameAndItsPrefix() {
    assertTrue(PreviewStateAdapters.isReservedNamespace("inventory"));
    assertTrue(PreviewStateAdapters.isReservedNamespace("surge"));
    assertTrue(PreviewStateAdapters.isReservedNamespace("vars"));
    assertTrue(PreviewStateAdapters.isReservedNamespace("cookTime"));
    assertTrue(PreviewStateAdapters.isReservedNamespace("blockType"));
    assertTrue(PreviewStateAdapters.isReservedNamespace("surge.active"));
    assertFalse(PreviewStateAdapters.isReservedNamespace("adapt"));
    assertFalse(PreviewStateAdapters.isReservedNamespace("test"));
  }

  @Test
  public void providerFailureDoesNotBreakTheRemainingVariables() {
    register(new PreviewStateProvider() {
      @Override
      public String namespace() {
        return "broken";
      }

      @Override
      public Map<String, Object> snapshot(Block block, Entity entity, Player player) {
        throw new IllegalStateException("boom");
      }
    });
    register(provider("test", Map.of("foo", 5)));
    PreviewStateContext context = PreviewStateContext.forBlock(PreviewFakes.chest(9).build(), null, Map.of());

    assertNull(context.variable("broken.foo"));
    assertEquals(5.0D, number(context, "test.foo"), EPSILON);
    assertEquals(9.0D, number(context, "inventory.size"), EPSILON);
  }

  @Test
  public void snapshotIsSampledOncePerGameTime() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().cookTime(100).cookTimeTotal(200).gameTime(1234L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(100.0D, number(context, "cookTime"), EPSILON);
    assertEquals(200.0D, number(context, "cookTimeTotal"), EPSILON);
    assertEquals(1, fake.calls("getCookTime"));

    fake.gameTime(1235L);
    assertEquals(100.0D, number(context, "cookTime"), EPSILON);
    assertEquals(2, fake.calls("getCookTime"));
  }

  /**
   * The tick and the map it produced must be published as one immutable pair. Two independent
   * volatile fields cannot guarantee that once more than one thread samples: one writer's map can
   * land between another writer's map and its tick, pairing a stale map with a fresh tick. This is
   * deliberately structural as well as behavioural, so reverting to separate fields fails here
   * rather than turning into a race nobody can reproduce.
   */
  @Test
  public void theTickAndItsMapArePublishedAsOneImmutablePair() throws Exception {
    for (Field field : PreviewStateContext.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && field.getType() == long.class) {
        fail("the sample tick must live inside the published pair, not in its own field: " + field.getName());
      }
    }

    Field field = sampleField();
    assertTrue("the sample field must be volatile", Modifier.isVolatile(field.getModifiers()));
    assertEquals(
        List.of("tick", "values"),
        Arrays.stream(field.getType().getRecordComponents()).map(RecordComponent::getName).toList()
    );

    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().cookTime(10).gameTime(100L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());
    field.setAccessible(true);

    assertEquals(10.0D, number(context, "cookTime"), EPSILON);
    PreviewStateContext.Sampled first = (PreviewStateContext.Sampled) field.get(context);

    fake.gameTime(101L).cookTime(200);
    assertEquals(200.0D, number(context, "cookTime"), EPSILON);
    PreviewStateContext.Sampled second = (PreviewStateContext.Sampled) field.get(context);

    // A re-sample publishes a brand new pair and leaves the old one wholly intact, so a reader still
    // holding the previous pair reads a tick and a map that came from the same sample.
    assertNotSame(first, second);
    assertEquals(100L, first.tick());
    assertEquals(101L, second.tick());
    assertEquals(10.0D, (Double) first.values().get("cookTime"), EPSILON);
    assertEquals(200.0D, (Double) second.values().get("cookTime"), EPSILON);
    assertEquals(100.0D, (Double) first.values().get("time"), EPSILON);
    assertEquals(101.0D, (Double) second.values().get("time"), EPSILON);
  }

  /** The observable half of the same contract: no lookup mixes values across two samples. */
  @Test
  public void everyLookupInOneTickReadsThatTicksSample() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().cookTime(10).burnTime(20).gameTime(100L);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(100.0D, number(context, "time"), EPSILON);
    fake.gameTime(101L).cookTime(200).burnTime(400);

    // time moves, so everything read after it must come from the new sample — never a mix.
    assertEquals(101.0D, number(context, "time"), EPSILON);
    assertEquals(200.0D, number(context, "cookTime"), EPSILON);
    assertEquals(400.0D, number(context, "burnTime"), EPSILON);
  }

  private static Field sampleField() {
    List<Field> pairs = Arrays.stream(PreviewStateContext.class.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .filter(field -> field.getType().isRecord())
        .toList();

    assertEquals("expected exactly one record-typed snapshot field", 1, pairs.size());
    return pairs.get(0);
  }

  // ---------------------------------------------------------------------
  // Functions
  // ---------------------------------------------------------------------

  @Test
  public void countAndOccupiedReadTheLiveInventory() {
    PreviewFakes.ContainerFake fake = PreviewFakes.chest(9).item(0, PreviewFakes.stack(Material.COAL, 3));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(3.0D, (Double) context.call("count", List.of(0.0D)), EPSILON);
    assertEquals(Boolean.TRUE, context.call("occupied", List.of(0.0D)));
    assertEquals(0.0D, (Double) context.call("count", List.of(1.0D)), EPSILON);
    assertEquals(Boolean.FALSE, context.call("occupied", List.of(1.0D)));
  }

  @Test
  public void countAndOccupiedAreSafeOutOfRangeAndWithoutAnInventory() {
    PreviewStateContext chest = PreviewStateContext.forBlock(PreviewFakes.chest(9).build(), null, Map.of());

    assertEquals(0.0D, (Double) chest.call("count", List.of(99.0D)), EPSILON);
    assertEquals(Boolean.FALSE, chest.call("occupied", List.of(99.0D)));
    assertEquals(0.0D, (Double) chest.call("count", List.of(-1.0D)), EPSILON);

    PreviewStateContext statics = PreviewStateContext.statics(Map.of());
    assertEquals(0.0D, (Double) statics.call("count", List.of(0.0D)), EPSILON);
    assertEquals(Boolean.FALSE, statics.call("occupied", List.of(0.0D)));
  }

  @Test
  public void emptyAndAirStacksDoNotCount() {
    PreviewFakes.ContainerFake fake = PreviewFakes.chest(9)
        .item(0, PreviewFakes.stack(Material.AIR, 1))
        .item(1, PreviewFakes.stack(Material.COAL, 0));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals(Boolean.FALSE, context.call("occupied", List.of(0.0D)));
    assertEquals(Boolean.FALSE, context.call("occupied", List.of(1.0D)));
    assertEquals(0.0D, number(context, "inventory.occupied"), EPSILON);
  }

  /**
   * The retired layouts drew a named container's own name instead of the theme title, and treated a
   * blank name as no name at all; both halves live in this variable now.
   */
  @Test
  public void customNameIsPublishedForANamedContainer() {
    PreviewFakes.ContainerFake named = PreviewFakes.chest(9).customName("Loot");
    assertEquals("Loot", PreviewStateContext.forBlock(named.build(), null, Map.of()).variable("customName"));

    PreviewFakes.ContainerFake blank = PreviewFakes.chest(9).customName("   ");
    assertEquals("", PreviewStateContext.forBlock(blank.build(), null, Map.of()).variable("customName"));

    assertEquals("", PreviewStateContext.forBlock(PreviewFakes.chest(9).build(), null, Map.of()).variable("customName"));
  }

  @Test
  public void customNameIsEmptyForBlocksAndTargetsThatCannotCarryOne() {
    assertEquals("", PreviewStateContext.forBlock(
        PreviewFakes.cauldron(Material.CAULDRON).build(), null, Map.of()).variable("customName"));
    assertEquals("", PreviewStateContext.statics(Map.of()).variable("customName"));
    assertEquals("", PreviewStateContext.forInventory(PreviewFakes.inventory(9).build(), Map.of()).variable("customName"));
  }

  @Test
  public void customNameIsPublishedForANamedEntity() {
    Entity entity = PreviewFakes.entity(EntityType.CHEST_MINECART)
        .inventory(PreviewFakes.inventory(27).build())
        .customName("Supplies")
        .build();

    assertEquals("Supplies", PreviewStateContext.forEntity(entity, null, Map.of()).variable("customName"));
  }

  @Test
  public void itemNamesTheMaterialInASlot() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace()
        .item(0, PreviewFakes.stack(Material.IRON_ORE, 1));
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());

    assertEquals("IRON_ORE", context.call("item", List.of(0.0D)));
    assertEquals("Iron Ore", ExprFunctions.call("readable", List.of(context.call("item", List.of(0.0D)))));
  }

  @Test
  public void itemIsEmptyForAnEmptySlotOrNoInventory() {
    PreviewStateContext chest = PreviewStateContext.forBlock(PreviewFakes.chest(9).build(), null, Map.of());

    assertEquals("", chest.call("item", List.of(0.0D)));
    assertEquals("", chest.call("item", List.of(99.0D)));
    assertEquals("", PreviewStateContext.statics(Map.of()).call("item", List.of(0.0D)));
  }

  @Test
  public void unknownFunctionsFallThroughToTheStandardLibrary() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    assertEquals(5.0D, (Double) context.call("clamp", List.of(9.0D, 0.0D, 5.0D)), EPSILON);
    assertNull(context.call("noSuchFunction", List.of()));
  }

  @Test
  public void langRendersACatalogMessageWithNoArguments() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    assertEquals("Idle", context.call("lang", List.of("holoui.preview.state.idle")));
  }

  /**
   * The strings on the right are what the golden snapshots record for the same state, so this is
   * the placeholder-binding half of what {@link GoldenEquivalenceTest} compares whole.
   */
  @Test
  public void langBindsPositionalArgumentsOntoTheTemplatePlaceholders() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    assertEquals(
        "Smelting Iron Ore 42%",
        context.call("lang", List.of("holoui.preview.state.smelting_item", "Iron Ore", 42.0D))
    );
    assertEquals("Fuel 5/20", context.call("lang", List.of("holoui.preview.stat.fuel_level", 5.0D, 20.0D)));
    assertEquals(
        "Bees 2/3   Honey 4/5",
        context.call("lang", List.of("holoui.preview.stat.bees_and_honey", 2.0D, 3.0D, 4.0D, 5.0D))
    );
  }

  @Test
  public void langArgumentsTakeTheKeyOwnPlaceholderNamesInFirstAppearanceOrder() {
    MessageArgs arguments = PreviewStateContext.langArguments(
        PreviewStateContext.messageKey("holoui.preview.state.smelting_item"),
        List.of("holoui.preview.state.smelting_item", "Iron Ore", 42.0D)
    );

    assertEquals(List.of("item", "percent"), new ArrayList<>(arguments.names()));
    assertEquals("Iron Ore", arguments.require("item").value());
    assertEquals("42", arguments.require("percent").value());
  }

  /** An id the catalog does not know has no placeholders, so its arguments fall back to position. */
  @Test
  public void langArgumentsFallBackToPositionalNamesForAnUnknownKey() {
    MessageArgs arguments = PreviewStateContext.langArguments(
        PreviewStateContext.messageKey("holoui.not.a.real.key"),
        List.of("holoui.not.a.real.key", 3.0D, "coal", Boolean.TRUE)
    );

    assertEquals(List.of("arg0", "arg1", "arg2"), new ArrayList<>(arguments.names()));
    assertEquals("3", arguments.require("arg0").value());
    assertEquals("coal", arguments.require("arg1").value());
    assertEquals("true", arguments.require("arg2").value());
  }

  @Test
  public void langRendersAnUnknownKeyAsItself() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    assertEquals("holoui.not.a.real.key", context.call("lang", List.of("holoui.not.a.real.key")));
  }

  /** Arguments past the last placeholder are named by position and simply go unused. */
  @Test
  public void langIgnoresArgumentsBeyondThePlaceholderCount() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    assertEquals(
        "Smelting Iron Ore 42%",
        context.call("lang", List.of("holoui.preview.state.smelting_item", "Iron Ore", 42.0D, "spare"))
    );
  }

  @Test
  public void langInsertsArgumentsAsUntrustedText() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    assertEquals(
        "Smelting &cRed 1%",
        context.call("lang", List.of("holoui.preview.state.smelting_item", "&cRed" + ChatColor.DARK_RED, 1.0D))
    );
  }

  @Test
  public void orderedPlaceholdersDedupesAndHonoursTheEscape() {
    assertEquals(List.of("a", "b"), PreviewStateContext.orderedPlaceholders("{a} {b} {a}"));
    assertEquals(List.of("b"), PreviewStateContext.orderedPlaceholders("{{a}} {b}"));
    assertEquals(List.of(), PreviewStateContext.orderedPlaceholders("no placeholders"));
    assertEquals(List.of(), PreviewStateContext.orderedPlaceholders("unclosed {a"));
  }

  @Test
  public void langRejectsAMissingOrNonStringKey() {
    PreviewStateContext context = PreviewStateContext.statics(Map.of());

    try {
      context.call("lang", List.of());
      fail("expected lang to reject a missing key");
    } catch (ExprException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("lang"));
    }
    try {
      context.call("lang", List.of(1.0D));
      fail("expected lang to reject a numeric key");
    } catch (ExprException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("lang"));
    }
  }

  // ---------------------------------------------------------------------
  // Catalog and surge tracker
  // ---------------------------------------------------------------------

  @Test
  public void catalogListsEveryDocumentedVariablePerCategory() {
    Map<String, Set<String>> catalog = PreviewStateAdapters.catalog();

    assertEquals(
        Set.of("universal", "inventory", "furnace", "brewing", "beehive", "cauldron", "jukebox"),
        catalog.keySet()
    );
    assertEquals(names("time", "blockType", "customName"), catalog.get("universal"));
    assertEquals(names("inventory.size", "inventory.occupied"), catalog.get("inventory"));
    assertEquals(
        names("cookTime", "cookTimeTotal", "burnTime", "fuelSeconds", "bankedXp", "lit", "surge.active", "surge.gain"),
        catalog.get("furnace")
    );
    assertEquals(
        names("brewTime", "brewTotal", "fuelLevel", "maxFuel", "surge.active", "surge.gain"),
        catalog.get("brewing")
    );
    assertEquals(names("bees", "maxBees", "honey", "maxHoney"), catalog.get("beehive"));
    assertEquals(names("level", "maxLevel", "fluid"), catalog.get("cauldron"));
    assertEquals(names("playing", "record"), catalog.get("jukebox"));
  }

  @Test
  public void everyCatalogedFurnaceVariableResolves() {
    PreviewFakes.FurnaceFake fake = PreviewFakes.furnace().cookTime(4).cookTimeTotal(8).burnTime(40);
    PreviewStateContext context = PreviewStateContext.forBlock(fake.build(), null, Map.of());
    Set<String> expected = new LinkedHashSet<>(PreviewStateAdapters.catalog().get("furnace"));
    expected.addAll(PreviewStateAdapters.catalog().get("universal"));
    expected.addAll(PreviewStateAdapters.catalog().get("inventory"));

    for (String name : expected) {
      assertTrue(name, context.variable(name) != null);
    }
  }

  @Test
  public void timeFlowTrackerHoldsTheSurgeForSixtyTicks() {
    TimeFlowTracker tracker = new TimeFlowTracker(false);

    tracker.sample(0L, 10);
    assertFalse(tracker.surging());

    tracker.sample(1L, 100);
    assertTrue(tracker.surging());
    assertEquals(4.45D, tracker.surgeSeconds(), EPSILON);

    tracker.sample(61L, 160);
    assertTrue(tracker.surging());
    tracker.sample(62L, 161);
    assertFalse(tracker.surging());
  }

  @Test
  public void timeFlowTrackerIgnoresSteadyProgress() {
    TimeFlowTracker tracker = new TimeFlowTracker(false);

    tracker.sample(0L, 10);
    tracker.sample(1L, 11);
    assertFalse(tracker.surging());
    assertEquals(0.0D, tracker.surgeSeconds(), EPSILON);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private void register(PreviewStateProvider provider) {
    PreviewStateProviders.register(provider);
    registered.add(provider);
  }

  private static PreviewStateProvider provider(String namespace, Map<String, Object> values) {
    return new PreviewStateProvider() {
      @Override
      public String namespace() {
        return namespace;
      }

      @Override
      public Map<String, Object> snapshot(Block block, Entity entity, Player player) {
        return values;
      }
    };
  }

  private static double number(PreviewStateContext context, String name) {
    Object value = context.variable(name);
    assertTrue(name + " -> " + value, value instanceof Double);
    return (Double) value;
  }

  private static Set<String> names(String... values) {
    return new LinkedHashSet<>(List.of(values));
  }
}
