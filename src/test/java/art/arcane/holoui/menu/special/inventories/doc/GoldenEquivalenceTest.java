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

import art.arcane.holoui.menu.special.inventories.PreviewElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The regression gate: every golden snapshot must be reproduced, field for field, by the shipped
 * JSON document that draws that container. The snapshots are the permanent record of what a preview
 * looks like — a document edit that moves a pixel fails here.
 *
 * <p>Each case loads the real resource from {@code src/main/resources/previews}, resolves the
 * variant variables the way {@code PreviewDocumentRegistry} will ({@code matchesBlock} /
 * {@code varsForBlock}, {@code matchesEntity} / {@code varsForEntity}), builds against a
 * {@link PreviewStateContext} over the same {@link GoldenFakes} state the capture used, and
 * serializes through the same {@link GoldenSerializer}. Comparison is on the parsed JSON trees so a
 * failure prints both sides whole rather than a truncated string diff.
 *
 * <p>{@link GoldenSerializer} evaluates each cell/label supplier exactly once, in list order, and
 * that is load-bearing here too: the furnace suppliers sample a {@link TimeFlowTracker} as a side
 * effect, so a second evaluation pass would change what the surge scenario renders.
 */
@RunWith(Parameterized.class)
public class GoldenEquivalenceTest {

  private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

  /** Builds the element list for one scenario from its already-parsed document. */
  private interface Build {
    List<PreviewElement> apply(CompiledPreviewDocument doc);
  }

  private record Case(String golden, String document, Build build) {
  }

  private final String golden;
  private final String document;
  private final Build build;

  public GoldenEquivalenceTest(String golden, String document, Build build) {
    this.golden = golden;
    this.document = document;
    this.build = build;
  }

  @Parameters(name = "{0}")
  public static List<Object[]> scenarios() {
    List<Object[]> parameters = new ArrayList<>();
    for (Case scenario : cases()) {
      parameters.add(new Object[]{scenario.golden(), scenario.document(), scenario.build()});
    }
    return parameters;
  }

  @Test
  public void documentReproducesTheGolden() throws IOException {
    CompiledPreviewDocument doc = parse(document);
    List<PreviewElement> elements = build.apply(doc);
    assertNotNull(golden, elements);

    JsonElement actual = JsonParser.parseString(GoldenSerializer.serialize(elements));
    JsonElement expected = JsonParser.parseString(readGolden(golden));
    if (!expected.equals(actual)) {
      fail(golden + " is not reproduced by previews/" + document + ".json"
          + "\nfirst difference " + firstDifference(expected, actual)
          + "\n--- golden ---\n" + PRETTY.toJson(expected)
          + "\n--- document ---\n" + PRETTY.toJson(actual));
    }
  }

  // ---------------------------------------------------------------------
  // Scenarios
  // ---------------------------------------------------------------------

  private static List<Case> cases() {
    List<Case> cases = new ArrayList<>();
    cases.add(new Case("chest_27", "chest", doc -> block(doc, GoldenFakes.chest(27), Material.CHEST)));
    cases.add(new Case("chest_54", "chest", doc -> block(doc, GoldenFakes.chest(54), Material.CHEST)));
    cases.add(new Case("trapped_chest", "chest", doc -> block(doc, GoldenFakes.trappedChest(), Material.TRAPPED_CHEST)));
    cases.add(new Case("barrel", "chest", doc -> block(doc, GoldenFakes.barrel(), Material.BARREL)));
    cases.add(new Case("copper_chest", "chest", doc -> block(doc, GoldenFakes.copperChest(), Material.COPPER_CHEST)));
    for (Material shulker : GoldenFakes.shulkerBoxes()) {
      cases.add(new Case(
          shulker.name().toLowerCase(Locale.ROOT),
          "chest",
          doc -> block(doc, GoldenFakes.shulkerBox(shulker), shulker)
      ));
    }
    cases.add(new Case("dispenser", "dispenser", doc -> block(doc, GoldenFakes.dispenser(), Material.DISPENSER)));
    cases.add(new Case("hopper", "hopper", doc -> block(doc, GoldenFakes.hopper(), Material.HOPPER)));
    cases.add(new Case("shelf_3", "shelf", doc -> block(doc, GoldenFakes.shelf(), Material.ACACIA_SHELF)));
    cases.add(new Case("chiseled_bookshelf", "chiseled_bookshelf",
        doc -> block(doc, GoldenFakes.chiseledBookshelf(), Material.CHISELED_BOOKSHELF)));
    cases.add(new Case("furnace_idle", "furnace", doc -> block(doc, GoldenFakes.furnaceIdle(), Material.FURNACE)));
    cases.add(new Case("furnace_smelting", "furnace",
        doc -> block(doc, GoldenFakes.smelting(Material.FURNACE), Material.FURNACE)));
    cases.add(new Case("furnace_surging", "furnace", GoldenEquivalenceTest::furnaceSurging));
    cases.add(new Case("blast_furnace_smelting", "furnace",
        doc -> block(doc, GoldenFakes.smelting(Material.BLAST_FURNACE), Material.BLAST_FURNACE)));
    cases.add(new Case("smoker_smelting", "furnace",
        doc -> block(doc, GoldenFakes.smelting(Material.SMOKER), Material.SMOKER)));
    cases.add(new Case("brewing_idle", "brewing_stand",
        doc -> block(doc, GoldenFakes.brewingIdle(), Material.BREWING_STAND)));
    cases.add(new Case("brewing_active", "brewing_stand",
        doc -> block(doc, GoldenFakes.brewingActive(), Material.BREWING_STAND)));
    cases.add(new Case("jukebox_empty", "jukebox", doc -> block(doc, GoldenFakes.jukeboxEmpty(), Material.JUKEBOX)));
    cases.add(new Case("jukebox_playing", "jukebox", doc -> block(doc, GoldenFakes.jukeboxPlaying(), Material.JUKEBOX)));
    cases.add(new Case("beehive", "beehive", doc -> block(doc, GoldenFakes.beehive(), Material.BEEHIVE)));
    cases.add(new Case("cauldron_empty", "cauldron", doc -> block(doc, GoldenFakes.cauldronEmpty(), Material.CAULDRON)));
    cases.add(new Case("cauldron_water_2", "cauldron",
        doc -> block(doc, GoldenFakes.cauldronWater(), Material.WATER_CAULDRON)));
    cases.add(new Case("cauldron_lava", "cauldron",
        doc -> block(doc, GoldenFakes.cauldronLava(), Material.LAVA_CAULDRON)));
    cases.add(new Case("cauldron_powder_snow", "cauldron",
        doc -> block(doc, GoldenFakes.cauldronPowderSnow(), Material.POWDER_SNOW_CAULDRON)));
    cases.add(new Case("minecart_hopper", "minecart", GoldenEquivalenceTest::minecartHopper));
    cases.add(new Case("ender_chest", "ender_chest", GoldenEquivalenceTest::enderChest));
    cases.add(new Case("locked", "locked", GoldenEquivalenceTest::locked));
    return cases;
  }

  private static List<PreviewElement> block(CompiledPreviewDocument doc, PreviewFakes.BlockFake<?> fake, Material material) {
    assertTrue(doc.name() + " does not match " + material, doc.matchesBlock(material));
    return doc.build(PreviewStateContext.forBlock(fake.build(), null, doc.varsForBlock(material)));
  }

  /**
   * Drives the new context's tracker exactly as the capture drove the old one: sample once at the
   * earlier tick with the lower cook counter, then advance both and build for real. That is 80
   * counter ticks gained in 30 game ticks, which is the surge condition. The priming read has to go
   * through the context (rather than through a discarded build) because the tracker lives on the
   * context, not on the elements.
   */
  private static List<PreviewElement> furnaceSurging(CompiledPreviewDocument doc) {
    PreviewFakes.FurnaceFake fake = GoldenFakes.surgingFurnace();
    PreviewStateContext context = PreviewStateContext.forBlock(
        fake.build(), null, doc.varsForBlock(Material.FURNACE));
    assertEquals(Boolean.FALSE, context.variable("surge.active"));

    fake.gameTime(GoldenFakes.GAME_TIME).cookTime(GoldenFakes.SURGE_COOK_TIME);
    List<PreviewElement> elements = doc.build(context);
    assertEquals("the surge scenario lost its surge", Boolean.TRUE, context.variable("surge.active"));
    return elements;
  }

  private static List<PreviewElement> minecartHopper(CompiledPreviewDocument doc) {
    Entity entity = GoldenFakes.hopperMinecart(GoldenFakes.minecartInventory()).build();
    assertTrue(doc.name() + " does not match the hopper minecart", doc.matchesEntity(entity));
    return doc.build(PreviewStateContext.forEntity(entity, null, doc.varsForEntity(entity)));
  }

  private static List<PreviewElement> enderChest(CompiledPreviewDocument doc) {
    assertEquals("enderChest", doc.special());
    Inventory inventory = GoldenFakes.enderChestInventory();
    return doc.build(PreviewStateContext.forInventory(inventory, doc.varsForBlock(Material.ENDER_CHEST)));
  }

  private static List<PreviewElement> locked(CompiledPreviewDocument doc) {
    assertEquals("locked", doc.special());
    return doc.build(PreviewStateContext.statics(doc.vars()));
  }

  // ---------------------------------------------------------------------
  // Resources
  // ---------------------------------------------------------------------

  private static CompiledPreviewDocument parse(String name) throws IOException {
    String resource = "/previews/" + name + ".json";
    try (InputStream stream = GoldenEquivalenceTest.class.getResourceAsStream(resource)) {
      assertNotNull("missing shipped document " + resource, stream);
      return PreviewDocumentParser.parse(name + ".json", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static String readGolden(String name) throws IOException {
    return Files.readString(
        Path.of(System.getProperty("user.dir"), "src", "test", "resources", "golden", name + ".json"),
        StandardCharsets.UTF_8
    );
  }

  /** Names the first element that differs, so a 59-element card does not have to be eyeballed whole. */
  private static String firstDifference(JsonElement expected, JsonElement actual) {
    JsonArray left = expected.getAsJsonArray();
    JsonArray right = actual.getAsJsonArray();
    int size = Math.min(left.size(), right.size());
    for (int index = 0; index < size; index++) {
      if (!left.get(index).equals(right.get(index))) {
        return "[" + index + "]\n  golden:   " + left.get(index) + "\n  document: " + right.get(index);
      }
    }
    return "element count " + left.size() + " (golden) vs " + right.size() + " (document)";
  }
}
