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
import art.arcane.holoui.util.common.TextUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Build-time behaviour of {@link CompiledPreviewDocument#build}: repeat expansion, visibility,
 * live-vs-build evaluation, colour narrowing, and the {@link CardFramer} chrome.
 *
 * <p>Every chrome coordinate asserted here was computed by hand from the {@link CardFramer}
 * arithmetic, not read back from the implementation — see
 * {@link #threeSlotRowBuildsChromeAndSlots} for the derivation.
 */
public class PreviewBuildTest {

  private static final int WELL_COLOR = 0xFF15151B;
  private static final int PANEL_COLOR = 0xF21B1B22;
  private static final int TRAY_COLOR = 0xFF33333E;

  // ---------------------------------------------------------------------
  // Chrome: the hand-computed 3-slot row
  // ---------------------------------------------------------------------

  /**
   * Three 18px slots at y=0, x = -20/0/20, over a 3-slot inventory, framed with accent #FF336699.
   *
   * <p>Hand-worked styled() arithmetic:
   * <pre>
   *   halfHeight (Slot)   = WELL/2 = 9      -> contentTop = 9, contentBottom = -9
   *   grid bounds         = left -29, right 29, bottom -9, top 9
   *   panelHalfWidth      = max(82, (29 - -29)/2 + 7 = 36)      = 82
   *   titleBarBottom      = contentTop + GAP = 9 + 6            = 15
   *   panelTop            = 15 + TITLE_BAR_HEIGHT(17)           = 32
   *   panelBottom         = contentBottom - PANEL_PAD(7)        = -16
   *   panelCenterY        = (32 + -16)/2                        = 8
   *   panelWidth          = 82 * 2                              = 164
   *   panelHeight         = 32 - -16                            = 48
   *   frameColor          = 0xCC &lt;&lt; 24 | 0x336699             = 0xCC336699
   *   titleBarColor       = 0xE6 &lt;&lt; 24 | 0x336699             = 0xE6336699
   *   tray                = w (29 - -29) + 8 = 66, h (9 - -9) + 8 = 26, centre (0, 0)
   *   titleBarCenterY     = (32 + 15)/2                         = 23
   * </pre>
   */
  @Test
  public void threeSlotRowBuildsChromeAndSlots() {
    Inventory inventory = PreviewFakes.inventory(3).build();
    CompiledPreviewDocument doc = parse("row.json", """
        {
          "card": { "title": "'Chest'", "accent": "#FF336699" },
          "elements": [
            {
              "type": "slot",
              "repeat": { "count": "inventory.size", "var": "i" },
              "x": "(i - (inventory.size - 1) / 2) * 20",
              "size": 18,
              "index": "i"
            }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(inventory, Map.of()));

    assertEquals(8, elements.size());
    assertPanel(elements.get(0), 0, 8, 0, 170, 54, 0xCC336699);
    assertPanel(elements.get(1), 0, 8, 1, 164, 48, PANEL_COLOR);
    assertPanel(elements.get(2), 0, 0, 2, 66, 26, TRAY_COLOR);
    assertPanel(elements.get(3), 0, 23, 3, 164, 17, 0xE6336699);
    assertLabel(elements.get(4), 0, 23, 6, "Chest", 0);
    assertSlot(elements.get(5), -20, 0, 4, 18, WELL_COLOR, inventory, 0);
    assertSlot(elements.get(6), 0, 0, 4, 18, WELL_COLOR, inventory, 1);
    assertSlot(elements.get(7), 20, 0, 4, 18, WELL_COLOR, inventory, 2);
  }

  /** A label-only card measures with LINE/2 and emits no tray, since nothing is cell-like. */
  @Test
  public void labelOnlyCardMeasuresWithLineHeightAndHasNoTray() {
    // halfHeight (Label) = LINE/2 = 6 -> contentTop 6, contentBottom -6; no grid.
    // panelHalfWidth = max(82, WELL/2 + PANEL_PAD = 16) = 82; titleBarBottom = 12; panelTop = 29;
    // panelBottom = -13; panelCenterY = (29 + -13)/2 = 8; panelHeight = 42; titleBarCenterY = 20.
    CompiledPreviewDocument doc = parse("labelonly.json", """
        {
          "card": { "title": "'Title'", "accent": "#FF336699" },
          "elements": [ { "type": "label", "text": "'hello'" } ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(5, elements.size());
    assertPanel(elements.get(0), 0, 8, 0, 170, 48, 0xCC336699);
    assertPanel(elements.get(1), 0, 8, 1, 164, 42, PANEL_COLOR);
    assertPanel(elements.get(2), 0, 20, 3, 164, 17, 0xE6336699);
    assertLabel(elements.get(3), 0, 20, 6, "Title", 0);
    assertLabel(elements.get(4), 0, 0, 6, "hello", 0);
  }

  @Test
  public void framedFalseYieldsContentOnly() {
    CompiledPreviewDocument doc = parse("bare.json", """
        {
          "card": { "framed": false, "title": "'Chest'" },
          "elements": [ { "type": "label", "text": "'hello'" } ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(1, elements.size());
    assertLabel(elements.get(0), 0, 0, 6, "hello", 0);
  }

  @Test
  public void cardWithoutFramedKeyIsFramed() {
    CompiledPreviewDocument doc = parse("defaultframed.json", """
        {
          "card": { "title": "'Chest'" },
          "elements": [ { "type": "label", "text": "'hello'" } ]
        }
        """);

    assertEquals(5, doc.build(PreviewStateContext.statics(Map.of())).size());
  }

  @Test
  public void noCardYieldsContentOnly() {
    CompiledPreviewDocument doc = parse("nocard.json", """
        { "elements": [ { "type": "label", "text": "'hello'" } ] }
        """);

    assertEquals(1, doc.build(PreviewStateContext.statics(Map.of())).size());
  }

  @Test
  public void framedCardWithoutTitleRendersAnEmptyTitleLabel() {
    CompiledPreviewDocument doc = parse("notitle.json", """
        {
          "card": { "framed": true },
          "elements": [ { "type": "label", "text": "'hello'" } ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(5, elements.size());
    assertEquals(Component.empty(), label(elements.get(3)).text().get());
    // Accent omitted: the default is the old theme's no-colour-code accent, 0xCBD0D9.
    assertEquals(0xCCCBD0D9, panel(elements.get(0)).color());
    assertEquals(0xE6CBD0D9, panel(elements.get(2)).color());
  }

  // ---------------------------------------------------------------------
  // Colour narrowing
  // ---------------------------------------------------------------------

  @Test
  public void foldedConstantColorNarrowsToUnsignedArgb() {
    CompiledPreviewDocument doc = parse("color.json", """
        {
          "elements": [
            { "type": "panel", "width": 10, "height": 10, "color": "#F21B1B22" },
            { "type": "cell", "size": 18, "color": "inventory.size > 0 ? #F21B1B22 : 0" },
            { "type": "label", "text": "'x'", "background": "#F21B1B22" }
          ]
        }
        """);
    Inventory inventory = PreviewFakes.inventory(1).build();

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(inventory, Map.of()));

    assertEquals(0xF21B1B22, panel(elements.get(0)).color());
    assertEquals(0xF21B1B22, cell(elements.get(1)).color().getAsInt());
    assertEquals(0xF21B1B22, label(elements.get(2)).backgroundColor());
  }

  // ---------------------------------------------------------------------
  // Repeat expansion
  // ---------------------------------------------------------------------

  @Test
  public void repeatCountComesFromLiveState() {
    CompiledPreviewDocument doc = parse("count.json", """
        {
          "elements": [
            {
              "type": "cell",
              "repeat": { "count": "inventory.size" },
              "x": "i * 20",
              "size": 18,
              "color": "#FF000000"
            }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(5).build(), Map.of()));

    assertEquals(5, elements.size());
    for (int index = 0; index < 5; index++) {
      assertEquals(index * 20, elements.get(index).x());
    }
  }

  @Test
  public void repeatVarIsFrozenPerInstanceInLiveColorClosures() {
    Inventory inventory = PreviewFakes.inventory(3)
        .item(1, PreviewFakes.stack(Material.STONE, 1))
        .build();
    CompiledPreviewDocument doc = parse("live.json", """
        {
          "elements": [
            {
              "type": "cell",
              "repeat": { "count": 3, "var": "slot" },
              "x": "slot * 20",
              "size": 18,
              "color": "occupied(slot) ? #FF00FF00 : #FF000000"
            }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(inventory, Map.of()));

    assertEquals(3, elements.size());
    assertEquals(0xFF000000, cell(elements.get(0)).color().getAsInt());
    assertEquals(0xFF00FF00, cell(elements.get(1)).color().getAsInt());
    assertEquals(0xFF000000, cell(elements.get(2)).color().getAsInt());
  }

  @Test
  public void zeroRepeatCountEmitsNothing() {
    CompiledPreviewDocument doc = parse("none.json", """
        {
          "elements": [
            { "type": "cell", "repeat": { "count": "inventory.size - 3" }, "size": 18, "color": "#FF000000" }
          ]
        }
        """);

    assertTrue(doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(3).build(), Map.of())).isEmpty());
  }

  /**
   * The parser can only bound constant repeat counts, so a document of dynamic repeats has to be
   * bounded again at build time. Each repeat is already clamped to 1024, so crossing the 4096
   * element cap takes five of them: four fill the budget exactly, the fifth is skipped whole.
   */
  @Test
  public void aggregateExpansionIsCappedAcrossElements() {
    Inventory inventory = PreviewFakes.inventory(1024).build();
    CompiledPreviewDocument doc = parse("flood.json", floodDocument(5, false));

    assertEquals(4096, doc.build(PreviewStateContext.forInventory(inventory, Map.of())).size());
  }

  /** The repeat that straddles the cap is truncated to what is left rather than skipped whole. */
  @Test
  public void repeatStraddlingTheCapIsTruncated() {
    Inventory inventory = PreviewFakes.inventory(1024).build();
    // One plain label, then four repeats of 1024: 1 + 1024 + 1024 + 1024 leaves 1023 for the last.
    CompiledPreviewDocument doc = parse("straddle.json", floodDocument(4, true));

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(inventory, Map.of()));

    assertEquals(4096, elements.size());
    assertTrue(elements.get(0) instanceof PreviewElement.Label);
  }

  private static String floodDocument(int repeats, boolean leadingLabel) {
    String cell = """
        { "type": "cell", "repeat": { "count": "inventory.size" }, "size": 18, "color": "#FF000000" }""";
    List<String> parts = new ArrayList<>();
    if (leadingLabel) {
      parts.add("{ \"type\": \"label\", \"text\": \"'x'\" }");
    }
    parts.addAll(Collections.nCopies(repeats, cell));
    return "{ \"elements\": [" + String.join(",", parts) + "] }";
  }

  // ---------------------------------------------------------------------
  // Visibility and skipping
  // ---------------------------------------------------------------------

  @Test
  public void visibleFalseSkipsTheElement() {
    CompiledPreviewDocument doc = parse("hidden.json", """
        {
          "elements": [
            { "type": "label", "text": "'gone'", "visible": "false" },
            { "type": "label", "text": "'kept'" }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(1, elements.size());
    assertEquals("kept", TextUtils.content(label(elements.get(0)).text().get()));
  }

  @Test
  public void visibleIsEvaluatedPerRepeatInstance() {
    CompiledPreviewDocument doc = parse("evens.json", """
        {
          "elements": [
            {
              "type": "cell",
              "repeat": { "count": 4 },
              "x": "i * 20",
              "size": 18,
              "color": "#FF000000",
              "visible": "i % 2 == 0"
            }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(2, elements.size());
    assertEquals(0, elements.get(0).x());
    assertEquals(40, elements.get(1).x());
  }

  @Test
  public void slotWithoutAnInventoryIsSkipped() {
    CompiledPreviewDocument doc = parse("noinv.json", """
        {
          "elements": [
            { "type": "slot", "size": 18, "index": 0 },
            { "type": "label", "text": "'kept'" }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(1, elements.size());
    assertTrue(elements.get(0) instanceof PreviewElement.Label);
  }

  // ---------------------------------------------------------------------
  // Runtime failure fallbacks
  // ---------------------------------------------------------------------

  /**
   * {@code record} is a cataloged variable (so it parses), but only the jukebox category samples
   * it — in an inventory context the lookup fails at render time, which is exactly the runtime
   * error the fallback exists for.
   */
  @Test
  public void brokenRuntimeColorFallsBackToTransparent() {
    CompiledPreviewDocument doc = parse("badcolor.json", """
        {
          "elements": [ { "type": "cell", "size": 18, "color": "record" } ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(1).build(), Map.of()));

    assertEquals(1, elements.size());
    assertEquals(0x00000000, cell(elements.get(0)).color().getAsInt());
  }

  /**
   * {@code build(context, errorSink)} routes every failure to the caller's sink too, unthrottled —
   * the channel {@code /holoui previews dump} reads from. A live cell colour closure only reports
   * once it is actually invoked (it is lazy), so the test polls it exactly like the renderer would.
   */
  @Test
  public void brokenLiveCellExpressionIsCapturedBySink() {
    CompiledPreviewDocument doc = parse("brokencell.json", """
        { "elements": [ { "type": "cell", "size": 18, "color": "record" } ] }
        """);
    List<String> errors = new ArrayList<>();

    List<PreviewElement> elements = doc.build(
        PreviewStateContext.forInventory(PreviewFakes.inventory(1).build(), Map.of()), errors::add);
    int color = cell(elements.get(0)).color().getAsInt();

    assertEquals(0x00000000, color);
    assertEquals(1, errors.size());
    assertTrue(errors.get(0), errors.get(0).startsWith("cell color:"));
  }

  /**
   * {@link CompiledPreviewDocument#throttledLog} caps the shared plugin logger at one line per
   * document per minute, but the sink is per-invocation: a second build of the same broken document
   * moments later — a second {@code /holoui previews dump} — must still report its own error.
   */
  @Test
  public void sinkReportsEveryBuildEvenWhenTheLoggerWouldThrottle() {
    CompiledPreviewDocument doc = parse("throttled.json", """
        { "elements": [ { "type": "cell", "size": "record", "color": "#FF000000" } ] }
        """);

    List<String> first = new ArrayList<>();
    doc.build(PreviewStateContext.statics(Map.of()), first::add);
    List<String> second = new ArrayList<>();
    doc.build(PreviewStateContext.statics(Map.of()), second::add);

    assertEquals(1, first.size());
    assertEquals(1, second.size());
  }

  /** The single-argument {@code build} is equivalent to a build with a sink that discards everything. */
  @Test
  public void buildWithoutASinkStillProducesElementsDespiteAFailure() {
    CompiledPreviewDocument doc = parse("noop.json", """
        {
          "elements": [
            { "type": "cell", "size": "record", "color": "#FF000000" },
            { "type": "label", "text": "'kept'" }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(1).build(), Map.of()));

    assertEquals(1, elements.size());
    assertEquals("kept", TextUtils.content(label(elements.get(0)).text().get()));
  }

  @Test
  public void brokenRuntimeLabelTextFallsBackToEmpty() {
    CompiledPreviewDocument doc = parse("badtext.json", """
        {
          "elements": [ { "type": "label", "text": "record" } ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(1).build(), Map.of()));

    assertEquals(1, elements.size());
    assertEquals(Component.empty(), label(elements.get(0)).text().get());
  }

  /** A build-time failure drops only its own element; the rest of the document still renders. */
  @Test
  public void brokenBuildTimeFieldSkipsOnlyThatElement() {
    CompiledPreviewDocument doc = parse("badsize.json", """
        {
          "elements": [
            { "type": "cell", "size": "record", "color": "#FF000000" },
            { "type": "label", "text": "'kept'" }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(1).build(), Map.of()));

    assertEquals(1, elements.size());
    assertEquals("kept", TextUtils.content(label(elements.get(0)).text().get()));
  }

  @Test
  public void brokenRepeatCountSkipsOnlyThatElement() {
    CompiledPreviewDocument doc = parse("badcount.json", """
        {
          "elements": [
            { "type": "cell", "repeat": { "count": "record" }, "size": 18, "color": "#FF000000" },
            { "type": "label", "text": "'kept'" }
          ]
        }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(PreviewFakes.inventory(1).build(), Map.of()));

    assertEquals(1, elements.size());
    assertEquals("kept", TextUtils.content(label(elements.get(0)).text().get()));
  }

  // ---------------------------------------------------------------------
  // Live vs build-time evaluation
  // ---------------------------------------------------------------------

  /**
   * {@code count()} reads the inventory directly rather than through the context's per-tick
   * snapshot, so the second call observes the mutation without waiting out the snapshot's tick.
   */
  @Test
  public void labelTextIsReEvaluatedPerCall() {
    PreviewFakes.InventoryFake fake = PreviewFakes.inventory(3);
    Inventory inventory = fake.build();
    CompiledPreviewDocument doc = parse("livetext.json", """
        { "elements": [ { "type": "label", "text": "'items ' + count(0)" } ] }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(inventory, Map.of()));
    assertEquals("items 0", TextUtils.content(label(elements.get(0)).text().get()));

    fake.item(0, PreviewFakes.stack(Material.STONE, 5));
    assertEquals("items 5", TextUtils.content(label(elements.get(0)).text().get()));
  }

  @Test
  public void positionsAreEvaluatedOnceAtBuild() {
    PreviewFakes.InventoryFake fake = PreviewFakes.inventory(3);
    Inventory inventory = fake.build();
    CompiledPreviewDocument doc = parse("staticpos.json", """
        { "elements": [ { "type": "cell", "x": "count(0) * 10", "size": 18, "color": "#FF000000" } ] }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.forInventory(inventory, Map.of()));
    fake.item(0, PreviewFakes.stack(Material.STONE, 3));

    assertEquals(0, elements.get(0).x());
  }

  /**
   * A constant label must not re-run {@link TextUtils#parse} on every poll — the renderer polls
   * these suppliers every four ticks, and the parse is a colour-code translation plus a MiniMessage
   * deserialize. Instance identity is the only way to tell a cached Component from a re-parsed one,
   * since two parses of the same string are equal.
   */
  @Test
  public void constantLabelTextIsParsedOnceAndCached() {
    CompiledPreviewDocument doc = parse("constanttext.json", """
        { "elements": [ { "type": "label", "text": "'&cdanger'" } ] }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));
    Supplier<Component> text = label(elements.get(0)).text();

    assertSame(text.get(), text.get());
    assertEquals(TextUtils.parse("&cdanger"), text.get());
  }

  @Test
  public void liveLabelTextIsReParsedOnEveryPoll() {
    CompiledPreviewDocument doc = parse("livecached.json", """
        { "elements": [ { "type": "label", "text": "'items ' + count(0)" } ] }
        """);

    List<PreviewElement> elements = doc.build(
        PreviewStateContext.forInventory(PreviewFakes.inventory(3).build(), Map.of()));
    Supplier<Component> text = label(elements.get(0)).text();

    assertNotSame(text.get(), text.get());
    assertEquals("items 0", TextUtils.content(text.get()));
  }

  @Test
  public void labelTextIsParsedThroughTheLegacyTextUtility() {
    CompiledPreviewDocument doc = parse("colored.json", """
        { "elements": [ { "type": "label", "text": "'&cdanger'" } ] }
        """);

    List<PreviewElement> elements = doc.build(PreviewStateContext.statics(Map.of()));

    assertEquals(TextUtils.parse("&cdanger"), label(elements.get(0)).text().get());
  }

  // ---------------------------------------------------------------------
  // Matchers and vars
  // ---------------------------------------------------------------------

  @Test
  public void matchesBlocksExactlyAndByGlob() {
    CompiledPreviewDocument doc = parse("match.json", """
        { "match": { "blocks": ["CHEST", "*_SHULKER_BOX"], "priority": 5 } }
        """);

    assertEquals("match.json", doc.name());
    assertEquals(5, doc.priority());
    assertTrue(doc.matchesBlock(Material.CHEST));
    assertTrue(doc.matchesBlock(Material.RED_SHULKER_BOX));
    assertFalse(doc.matchesBlock(Material.BARREL));
    assertNull(doc.special());
  }

  @Test
  public void variantMatchersAlsoMatchTheDocument() {
    CompiledPreviewDocument doc = parse("variants.json", """
        {
          "match": { "blocks": ["CHEST"], "vars": { "accent": 1.0, "cols": 9.0 } },
          "variants": [ { "blocks": ["BARREL"], "vars": { "accent": 2.0 } } ]
        }
        """);

    assertTrue(doc.matchesBlock(Material.BARREL));
    assertEquals(Map.of("accent", 1.0, "cols", 9.0), doc.varsForBlock(Material.CHEST));
    assertEquals(Map.of("accent", 2.0, "cols", 9.0), doc.varsForBlock(Material.BARREL));
  }

  @Test
  public void matchesEntitiesByTypeName() {
    CompiledPreviewDocument doc = parse("entity.json", """
        {
          "match": { "entities": ["CHEST_MINECART"], "special": "anyInventoryHolder", "vars": { "cols": 9.0 } },
          "variants": [ { "entities": ["*_BOAT"], "vars": { "cols": 3.0 } } ]
        }
        """);
    Entity minecart = PreviewFakes.entity(EntityType.CHEST_MINECART).build();
    Entity boat = PreviewFakes.entity(EntityType.OAK_CHEST_BOAT).build();
    Entity pig = PreviewFakes.entity(EntityType.PIG).build();

    assertTrue(doc.matchesEntity(minecart));
    assertTrue(doc.matchesEntity(boat));
    assertFalse(doc.matchesEntity(pig));
    assertEquals(Map.of("cols", 9.0), doc.varsForEntity(minecart));
    assertEquals(Map.of("cols", 3.0), doc.varsForEntity(boat));
    assertEquals("anyInventoryHolder", doc.special());
  }

  @Test
  public void resolvedCarriesTheDocumentAndItsVars() {
    CompiledPreviewDocument doc = parse("resolved.json", "{ \"match\": { \"vars\": { \"cols\": 9.0 } } }");
    CompiledPreviewDocument.Resolved resolved = new CompiledPreviewDocument.Resolved(doc, doc.vars());

    assertSame(doc, resolved.doc());
    assertEquals(Map.of("cols", 9.0), resolved.vars());
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static CompiledPreviewDocument parse(String name, String json) {
    return PreviewDocumentParser.parse(name, json);
  }

  private static PreviewElement.Panel panel(PreviewElement element) {
    assertTrue(String.valueOf(element), element instanceof PreviewElement.Panel);
    return (PreviewElement.Panel) element;
  }

  private static PreviewElement.Cell cell(PreviewElement element) {
    assertTrue(String.valueOf(element), element instanceof PreviewElement.Cell);
    return (PreviewElement.Cell) element;
  }

  private static PreviewElement.Label label(PreviewElement element) {
    assertTrue(String.valueOf(element), element instanceof PreviewElement.Label);
    return (PreviewElement.Label) element;
  }

  private static void assertPanel(PreviewElement element, int x, int y, int z, int width, int height, int color) {
    PreviewElement.Panel panel = panel(element);
    assertEquals("x", x, panel.x());
    assertEquals("y", y, panel.y());
    assertEquals("z", z, panel.z());
    assertEquals("width", width, panel.width());
    assertEquals("height", height, panel.height());
    assertEquals("color", color, panel.color());
  }

  private static void assertSlot(PreviewElement element, int x, int y, int z, int size, int wellColor, Inventory inventory, int slot) {
    assertTrue(String.valueOf(element), element instanceof PreviewElement.Slot);
    PreviewElement.Slot cell = (PreviewElement.Slot) element;
    assertEquals("x", x, cell.x());
    assertEquals("y", y, cell.y());
    assertEquals("z", z, cell.z());
    assertEquals("size", size, cell.size());
    assertEquals("wellColor", wellColor, cell.wellColor());
    assertSame("inventory", inventory, cell.inventory());
    assertEquals("slot", slot, cell.slot());
  }

  private static void assertLabel(PreviewElement element, int x, int y, int z, String text, int background) {
    PreviewElement.Label label = label(element);
    assertEquals("x", x, label.x());
    assertEquals("y", y, label.y());
    assertEquals("z", z, label.z());
    assertEquals("text", text, TextUtils.content(label.text().get()));
    assertEquals("background", background, label.backgroundColor());
  }
}
