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
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.RideableMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Registry behaviour that never touches Bukkit's scheduler: extraction, loading, reload semantics,
 * match resolution, and the raycast eligibility set. {@code startWatching} is deliberately not
 * exercised here — the constructor performs the whole load, so every rule below is reachable by
 * calling {@link PreviewDocumentRegistry#reload()} directly, which is exactly what the watcher
 * tasks do once a file changes.
 *
 * <p>The last two tests are the equivalence gate that outlives Task 10's deletion of
 * {@code MenuSessionManager.isPreviewBlockType}: its material list is ported here verbatim and
 * asserted against what the thirteen shipped documents actually claim.
 */
public class PreviewRegistryTest {

  private static final String CELL = "{ \"type\": \"cell\", \"x\": 0, \"y\": 0, \"size\": 4, \"color\": \"#FFFFFFFF\" }";

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private File configDir;

  @Before
  public void createConfigDir() throws IOException {
    configDir = temp.newFolder("plugin");
  }

  // ---------------------------------------------------------------------
  // Extraction
  // ---------------------------------------------------------------------

  // ---------------------------------------------------------------------
  // get(name)
  // ---------------------------------------------------------------------

  @Test
  public void getReturnsTheCompiledDocumentByName() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("furnace.json", registry.get("furnace").name());
  }

  @Test
  public void getReturnsNullForAnUnknownOrNullName() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertNull(registry.get("not_a_document"));
    assertNull(registry.get(null));
  }

  @Test
  public void extractionWritesEveryShippedDocument() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals(Set.copyOf(ShippedPreviewDocumentTest.SHIPPED), Set.copyOf(PreviewDocumentRegistry.SHIPPED));
    assertEquals(Set.copyOf(PreviewDocumentRegistry.SHIPPED), registry.names());
    for (String name : PreviewDocumentRegistry.SHIPPED) {
      assertTrue(name, document(name).isFile());
    }
  }

  @Test
  public void aSecondConstructionLeavesAnEditedDocumentAlone() throws IOException {
    new PreviewDocumentRegistry(configDir);
    String edited = doc("[\"CHEST\"]", 20);
    Files.writeString(document("chest").toPath(), edited, StandardCharsets.UTF_8);

    new PreviewDocumentRegistry(configDir);

    assertEquals(edited, Files.readString(document("chest").toPath(), StandardCharsets.UTF_8));
  }

  @Test
  public void resetToDefaultRestoresTheShippedBytes() throws IOException {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    Files.writeString(document("furnace").toPath(), doc("[\"FURNACE\"]", 20), StandardCharsets.UTF_8);

    assertEquals(List.of("furnace"), registry.resetToDefault("furnace"));

    assertArrayEquals(shipped("furnace"), Files.readAllBytes(document("furnace").toPath()));
    assertEquals("furnace.json", registry.forBlock(Material.FURNACE).doc().name());
  }

  @Test
  public void resetToDefaultStarRestoresEveryShippedDocument() throws IOException {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    Files.writeString(document("hopper").toPath(), "{ broken", StandardCharsets.UTF_8);

    assertEquals(PreviewDocumentRegistry.SHIPPED, registry.resetToDefault("*"));

    assertArrayEquals(shipped("hopper"), Files.readAllBytes(document("hopper").toPath()));
  }

  @Test
  public void resetToDefaultIgnoresAnUnknownName() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals(List.of(), registry.resetToDefault("not_a_document"));
  }

  /**
   * {@code normalize} is public so {@code HoloPreviewsCommand.dump} can give a name arg the same
   * trailing-".json" tolerance {@code resetToDefault} already gets through this method.
   */
  @Test
  public void normalizeStripsATrailingJsonExtension() {
    assertEquals("furnace", PreviewDocumentRegistry.normalize("furnace.json"));
    assertEquals("furnace", PreviewDocumentRegistry.normalize("furnace"));
    assertEquals("*", PreviewDocumentRegistry.normalize("*"));
    assertEquals("", PreviewDocumentRegistry.normalize(null));
  }

  // ---------------------------------------------------------------------
  // Loading and reload semantics
  // ---------------------------------------------------------------------

  @Test
  public void aBrokenDocumentOnFirstLoadIsSkipped() throws IOException {
    write("broken", "{ this is not json");

    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertFalse(registry.names().contains("broken"));
    assertEquals(Set.copyOf(PreviewDocumentRegistry.SHIPPED), registry.names());
  }

  @Test
  public void aBrokenDocumentOnReloadRetainsThePreviousVersion() throws IOException {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    assertEquals("furnace.json", registry.forBlock(Material.FURNACE).doc().name());

    Files.writeString(document("furnace").toPath(), "{ \"elements\": [ { \"type\": \"cell\" } ] }", StandardCharsets.UTF_8);
    registry.reload();

    assertTrue(registry.names().contains("furnace"));
    assertEquals("furnace.json", registry.forBlock(Material.FURNACE).doc().name());
    assertTrue(registry.isPreviewBlockType(Material.BLAST_FURNACE));
  }

  /**
   * A pathologically nested expression used to throw {@code StackOverflowError} out of
   * {@code ExprParser} — an {@link Error}, not a {@code RuntimeException} — which escaped
   * {@code load()}'s old catch clause entirely. On first load that must skip the one document,
   * not abort constructing the registry (which is what plugin enable does).
   */
  @Test
  public void aPathologicallyNestedExpressionOnFirstLoadIsSkippedNotFatal() throws IOException {
    write("pathological", pathologicalDocument());

    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertFalse(registry.names().contains("pathological"));
    assertEquals(Set.copyOf(PreviewDocumentRegistry.SHIPPED), registry.names());
    assertEquals("furnace.json", registry.forBlock(Material.FURNACE).doc().name());
  }

  /** Reload counterpart: the watcher task must survive the same failure and keep the old version live. */
  @Test
  public void aPathologicallyNestedExpressionOnReloadRetainsThePreviousVersion() throws IOException {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    assertEquals("furnace.json", registry.forBlock(Material.FURNACE).doc().name());

    Files.writeString(document("furnace").toPath(), pathologicalDocument(), StandardCharsets.UTF_8);
    registry.reload();

    assertTrue(registry.names().contains("furnace"));
    assertEquals("furnace.json", registry.forBlock(Material.FURNACE).doc().name());
    assertTrue(registry.isPreviewBlockType(Material.BLAST_FURNACE));
  }

  @Test
  public void aNewDocumentIsPickedUpOnReload() throws IOException {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    assertFalse(registry.isPreviewBlockType(Material.CRAFTING_TABLE));

    write("workbench", doc("[\"CRAFTING_TABLE\"]", 20));
    registry.reload();

    assertTrue(registry.names().contains("workbench"));
    assertTrue(registry.isPreviewBlockType(Material.CRAFTING_TABLE));
    assertEquals("workbench.json", registry.forBlock(Material.CRAFTING_TABLE).doc().name());
  }

  /**
   * One reload is the whole hot-reload pass: the watcher task hands changed, created, and deleted
   * files to the same handler in one go, so all three have to land together.
   */
  @Test
  public void oneReloadAppliesEditsCreationsAndDeletionsTogether() throws IOException {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    Files.writeString(document("jukebox").toPath(), doc("[\"JUKEBOX\", \"NOTE_BLOCK\"]", 20), StandardCharsets.UTF_8);
    write("workbench", doc("[\"CRAFTING_TABLE\"]", 20));
    assertTrue(document("shelf").delete());
    registry.reload();

    assertTrue(registry.isPreviewBlockType(Material.NOTE_BLOCK));
    assertTrue(registry.isPreviewBlockType(Material.CRAFTING_TABLE));
    assertFalse(registry.isPreviewBlockType(Material.BAMBOO_SHELF));
    assertFalse(registry.names().contains("shelf"));
  }

  @Test
  public void aDeletedDocumentDropsOutOnReload() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    assertTrue(registry.isPreviewBlockType(Material.BAMBOO_SHELF));

    assertTrue(document("shelf").delete());
    registry.reload();

    assertFalse(registry.names().contains("shelf"));
    assertFalse(registry.isPreviewBlockType(Material.BAMBOO_SHELF));
    assertNull(registry.forBlock(Material.BAMBOO_SHELF));
  }

  // ---------------------------------------------------------------------
  // Resolution
  // ---------------------------------------------------------------------

  @Test
  public void theHighestPriorityDocumentWins() throws IOException {
    write("aa_low", doc("[\"CHEST\"]", 5));
    write("zz_high", doc("[\"CHEST\"]", 20));

    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("zz_high.json", registry.forBlock(Material.CHEST).doc().name());
  }

  @Test
  public void anExactMatchBeatsAGlobAtTheSamePriority() throws IOException {
    write("aa_glob", doc("[\"*_SHELF\"]", 20));
    write("zz_exact", doc("[\"BAMBOO_SHELF\"]", 20));

    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("zz_exact.json", registry.forBlock(Material.BAMBOO_SHELF).doc().name());
    assertEquals("aa_glob.json", registry.forBlock(Material.OAK_SHELF).doc().name());
  }

  @Test
  public void documentNameOrderBreaksAnOtherwiseEqualTie() throws IOException {
    write("aa_tie", doc("[\"JUKEBOX\"]", 20));
    write("zz_tie", doc("[\"JUKEBOX\"]", 20));

    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("aa_tie.json", registry.forBlock(Material.JUKEBOX).doc().name());
  }

  @Test
  public void aGlobResolvesWhenNoDocumentNamesTheMaterial() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("shelf.json", registry.forBlock(Material.BAMBOO_SHELF).doc().name());
    assertEquals("chest.json", registry.forBlock(Material.WAXED_EXPOSED_COPPER_CHEST).doc().name());
  }

  @Test
  public void anUnclaimedMaterialResolvesToNothing() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertNull(registry.forBlock(Material.CRAFTING_TABLE));
    assertNull(registry.forBlock(Material.AIR));
    assertNull(registry.forBlock(null));
    assertFalse(registry.isPreviewBlockType(Material.CRAFTING_TABLE));
    assertFalse(registry.isPreviewBlockType(Material.AIR));
    assertFalse(registry.isPreviewBlockType(null));
  }

  @Test
  public void variantVariablesTravelWithTheResolution() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    CompiledPreviewDocument.Resolved chest = registry.forBlock(Material.CHEST);
    CompiledPreviewDocument.Resolved trapped = registry.forBlock(Material.TRAPPED_CHEST);

    assertEquals("chest.json", trapped.doc().name());
    assertEquals(chest.doc(), trapped.doc());
    assertNotEquals(chest.vars(), trapped.vars());
    assertEquals(chest.doc().varsForBlock(Material.TRAPPED_CHEST), trapped.vars());
  }

  @Test
  public void specialTargetsResolveByMarker() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("ender_chest.json", registry.special(PreviewDocumentRegistry.SPECIAL_ENDER_CHEST).doc().name());
    assertEquals("locked.json", registry.special(PreviewDocumentRegistry.SPECIAL_LOCKED).doc().name());
    assertNull(registry.special("no_such_marker"));
    assertNull(registry.special(null));
  }

  /**
   * The condition {@code MenuSessionManager} branches on to build a preview from the viewer's own
   * ender chest: the marker on the document that <em>wins the block resolution</em>, not the block
   * material. Shipped, that is {@code ender_chest.json}. The session path itself needs a live
   * player, so only the decision is testable here.
   */
  @Test
  public void theEnderChestSessionBranchFollowsTheWinningDocument() throws IOException {
    PreviewDocumentRegistry shippedOnly = new PreviewDocumentRegistry(configDir);
    assertEquals(
        PreviewDocumentRegistry.SPECIAL_ENDER_CHEST,
        shippedOnly.forBlock(Material.ENDER_CHEST).doc().special());

    write("zz_user_ender", doc("[\"ENDER_CHEST\"]", 20));
    PreviewDocumentRegistry overridden = new PreviewDocumentRegistry(configDir);

    // A higher-priority user document takes the block, so the block takes the ordinary path and is
    // drawn from its own tile entity rather than the viewer's ender chest.
    assertEquals("zz_user_ender.json", overridden.forBlock(Material.ENDER_CHEST).doc().name());
    assertNull(overridden.forBlock(Material.ENDER_CHEST).doc().special());
    // The marker lookup still finds the shipped document, which is what the ender-chest session
    // builds from once the branch is taken.
    assertEquals(
        "ender_chest.json",
        overridden.special(PreviewDocumentRegistry.SPECIAL_ENDER_CHEST).doc().name());
  }

  // ---------------------------------------------------------------------
  // Entities
  // ---------------------------------------------------------------------

  @Test
  public void namedEntitiesResolveAheadOfTheFallback() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertEquals("minecart.json", registry.forEntity(storageMinecart()).doc().name());
    assertEquals("minecart.json", registry.forEntity(chestBoat()).doc().name());
    assertNull(registry.forEntity(rideableMinecart()));
    assertNull(registry.forEntity(null));
  }

  @Test
  public void theEntityGateStillRequiresAnInventoryHoldingCartOrBoat() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    assertTrue(registry.isPreviewEntity(storageMinecart()));
    assertTrue(registry.isPreviewEntity(hopperMinecart()));
    assertTrue(registry.isPreviewEntity(chestBoat()));
    assertFalse(registry.isPreviewEntity(rideableMinecart()));
    assertFalse(registry.isPreviewEntity(PreviewFakes.entity(EntityType.ZOMBIE).build()));
    assertFalse(registry.isPreviewEntity(null));
  }

  /**
   * The fallback document is what keeps an inventory-holding cart the shipped names do not list
   * previewable, and it must never make a block raycast-eligible: with every other document gone,
   * nothing is.
   */
  @Test
  public void theAnyInventoryHolderFallbackContributesNoRaycastMaterials() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);
    for (String name : PreviewDocumentRegistry.SHIPPED) {
      if (!name.equals("minecart")) {
        assertTrue(name, document(name).delete());
      }
    }
    registry.reload();

    assertEquals(Set.of("minecart"), registry.names());
    for (Material material : Material.values()) {
      assertFalse(material.name(), registry.isPreviewBlockType(material));
    }
    assertNull(registry.forBlock(Material.CHEST));

    Entity unnamed = PreviewFakes.entity(EntityType.MINECART).as(StorageMinecart.class).build();
    assertNotNull(registry.forEntity(unnamed));
    assertEquals("minecart.json", registry.forEntity(unnamed).doc().name());
  }

  // ---------------------------------------------------------------------
  // Equivalence gate: the retired MenuSessionManager.isPreviewBlockType list
  // ---------------------------------------------------------------------

  @Test
  public void raycastEligibilityMatchesTheRetiredMaterialList() {
    PreviewDocumentRegistry registry = new PreviewDocumentRegistry(configDir);

    EnumSet<Material> eligible = EnumSet.noneOf(Material.class);
    for (Material material : Material.values()) {
      if (registry.isPreviewBlockType(material)) {
        eligible.add(material);
      }
    }

    assertEquals(retiredList(false), eligible);
  }

  /**
   * The retired switch also claimed the seventeen pre-flattening {@code LEGACY_*_SHULKER_BOX}
   * constants, which no {@code Block.getType()} can ever return. The documents do not name them and
   * the registry does not synthesise them; this pins that the whole difference is those constants.
   */
  @Test
  public void theRetiredListsOnlyExtraMaterialsWereUnreachableLegacyConstants() {
    EnumSet<Material> extra = EnumSet.copyOf(retiredList(true));
    extra.removeAll(retiredList(false));

    assertFalse(extra.isEmpty());
    for (Material material : extra) {
      assertTrue(material.name(), material.isLegacy());
    }
  }

  /** Verbatim port of {@code MenuSessionManager.isPreviewBlockType} as it stood before Task 8. */
  private static EnumSet<Material> retiredList(boolean includeLegacy) {
    EnumSet<Material> expected = EnumSet.noneOf(Material.class);
    for (Material type : Material.values()) {
      if ((includeLegacy || !type.isLegacy()) && retired(type)) {
        expected.add(type);
      }
    }
    return expected;
  }

  private static boolean retired(Material type) {
    if (type == null || type == Material.AIR) {
      return false;
    }
    if (type.name().endsWith("SHULKER_BOX")) {
      return true;
    }
    if (type.name().endsWith("COPPER_CHEST")) {
      return true;
    }
    if (type.name().endsWith("_SHELF")) {
      return true;
    }
    return switch (type) {
      case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL, DISPENSER, DROPPER, HOPPER, FURNACE,
           BLAST_FURNACE, SMOKER, BEEHIVE, BEE_NEST, CAULDRON, WATER_CAULDRON,
           LAVA_CAULDRON, POWDER_SNOW_CAULDRON, JUKEBOX, BREWING_STAND, CHISELED_BOOKSHELF -> true;
      default -> false;
    };
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static Entity storageMinecart() {
    return PreviewFakes.entity(EntityType.CHEST_MINECART).as(StorageMinecart.class).build();
  }

  private static Entity hopperMinecart() {
    return PreviewFakes.entity(EntityType.HOPPER_MINECART).as(HopperMinecart.class).build();
  }

  private static Entity chestBoat() {
    return PreviewFakes.entity(EntityType.OAK_CHEST_BOAT).as(ChestBoat.class).build();
  }

  private static Entity rideableMinecart() {
    return PreviewFakes.entity(EntityType.MINECART).as(RideableMinecart.class).build();
  }

  private static String doc(String blocks, int priority) {
    return "{ \"match\": { \"blocks\": " + blocks + ", \"priority\": " + priority + " },"
        + " \"elements\": [ " + CELL + " ] }";
  }

  /** A document whose {@code x} field is an expression nested past {@code ExprParser}'s depth cap. */
  private static String pathologicalDocument() {
    String nested = "(".repeat(5000) + "1" + ")".repeat(5000);
    return "{ \"elements\": [ { \"type\": \"cell\", \"size\": 4, \"color\": 1, \"x\": \"" + nested + "\" } ] }";
  }

  private File document(String name) {
    return new File(new File(configDir, "previews"), name + ".json");
  }

  private void write(String name, String json) throws IOException {
    File file = document(name);
    assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
    Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
  }

  private static byte[] shipped(String name) throws IOException {
    try (InputStream stream = PreviewRegistryTest.class.getResourceAsStream("/previews/" + name + ".json")) {
      assertNotNull(name, stream);
      return stream.readAllBytes();
    }
  }
}
