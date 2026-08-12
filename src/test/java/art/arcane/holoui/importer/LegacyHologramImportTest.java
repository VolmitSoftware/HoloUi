package art.arcane.holoui.importer;

import art.arcane.holoui.menu.MenuTransform;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyHologramImportTest {
  private static final UUID WORLD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000801");

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void gholoExporterDocumentConvertsTextAndStaticIconRows() throws IOException {
    Path plugins = temp.newFolder("plugins").toPath();
    Path source = plugins.resolve("GHolo/holos");
    Files.createDirectories(source);
    Files.writeString(source.resolve("Welcome Board.yml"), """
        Holo:
          location:
            world: world
            x: 10.5
            y: 70
            z: -4
          data:
            range: 128
            permission: example.holograms.welcome
            backgroundColor: '#11223344'
            textShadow: true
            billboard: fixed
            scale: {x: 1.2, y: 1.5, z: 0.8}
            rotation: {yaw: 35, pitch: -12}
            size:
              width: 2
              height: 3
          rows:
            - content: '#12AB34Welcome &aFriend'
              offset: {x: 0, y: 0.5, z: 0}
              data:
                range: 32
                permission: example.row
                rotation: {yaw: 45}
            - content: 'block:minecraft:stone'
              offset: {x: 0.5, y: 0.2, z: -0.3}
            - content: 'item:minecraft:diamond'
              offset: {x: -0.4, y: 0.1, z: 0.7}
            - content: 'itemstack:minecraft:emerald'
              offset: {x: 0.2, y: -0.1, z: 0.4}
            - content: 'entity:minecraft:parrot'
              offset: {x: 1, y: 2, z: 3}
            - content: 'block:%viewer_block%'
              offset: {x: -1, y: -2, z: -3}
        """);

    LegacyHologramScanner scanner = new LegacyHologramScanner();
    LegacyHologramScanner.LegacyScanResult scan = scanner.scan(
        LegacyImportSource.GHOLO, source, plugins);
    assertTrue(scan.sourcePresent());
    assertTrue(scan.issues().isEmpty());
    assertEquals(1, scan.drafts().size());

    LegacyHologramConverter converter = new LegacyHologramConverter();
    LegacyHologramConverter.ConversionResult conversion = converter.convert(
        LegacyImportSource.GHOLO, scan.drafts(), worlds());
    assertTrue(conversion.issues().isEmpty());
    LegacyImportCandidate candidate = conversion.candidates().getFirst();
    assertEquals("imports/gholo/welcome-board", candidate.boardId());
    assertEquals("imports/gholo/welcome-board", candidate.menuId());
    assertEquals(128.0D, candidate.board().visibility().viewRange(), 0.0D);
    assertEquals("example.holograms.welcome", candidate.board().visibility().viewPermission());
    assertEquals(-145.0D, candidate.board().transform().yaw(), 0.0D);
    assertEquals(-12.0D, candidate.board().transform().pitch(), 0.0D);

    JsonArray components = JsonParser.parseString(candidate.menuSource()).getAsJsonObject()
        .getAsJsonArray("components");
    assertEquals("text", icon(components, 0).get("type").getAsString());
    assertEquals("<color:#12AB34>Welcome &aFriend",
        icon(components, 0).get("text").getAsString());
    assertEquals("block", icon(components, 1).get("type").getAsString());
    assertEquals("item", icon(components, 2).get("type").getAsString());
    assertEquals("item", icon(components, 3).get("type").getAsString());
    assertEquals("entity", icon(components, 4).get("type").getAsString());
    assertEquals("text", icon(components, 5).get("type").getAsString());
    assertEquals(2.0D, icon(components, 1).getAsJsonObject("style")
        .get("viewRange").getAsDouble(), 0.0D);
    assertEquals(2.0D, icon(components, 1).getAsJsonObject("style")
        .get("cullingWidth").getAsDouble(), 0.0D);
    assertEquals(2.0D, icon(components, 0).getAsJsonObject("style")
        .get("cullingWidth").getAsDouble(), 0.0D);
    assertEquals(1.6D, icon(components, 1).getAsJsonObject("style")
        .get("scaleX").getAsDouble(), 0.000001D);
    assertFalse(icon(components, 4).has("width"));
    assertFalse(icon(components, 4).has("height"));

    MenuTransform transform = new MenuTransform(new Location(null, 0.0D, 0.0D, 0.0D),
        new Vector(), (float) candidate.board().transform().yaw(),
        (float) candidate.board().transform().pitch(), 0.0F, 1.0F);
    assertEquals(35.0D, transform.displayYaw(), 0.0D);
    assertWorldOffset(transform, components, 0,
        new Vector(0.0D, -2.0D * 3.5D / 16.0D * 1.5D, 0.0D),
        new Vector(0.0D, 0.5D, 0.0D));
    assertWorldOffset(transform, components, 1, new Vector(0.0D, -0.05D, 0.0D),
        new Vector(0.5D, 0.2D, -0.3D));
    assertWorldOffset(transform, components, 2,
        new Vector(0.0D, -1.09D - 3.5D / 16.0D * 1.5D, 0.0D),
        new Vector(-0.4D, 0.1D, 0.7D));
    assertWorldOffset(transform, components, 3,
        new Vector(0.0D, -1.09D - 3.5D / 16.0D, 0.0D),
        new Vector(0.2D, -0.1D, 0.4D));
    assertWorldOffset(transform, components, 4, new Vector(), new Vector(1.0D, 2.0D, 3.0D));
    assertWorldOffset(transform, components, 5,
        new Vector(0.0D, -2.0D * 3.5D / 16.0D * 1.5D, 0.0D),
        new Vector(-1.0D, -2.0D, -3.0D));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("row-specific range")));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("row-specific permission")));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("row-specific rotation")));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("dropped-item")));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("preserved as text")));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("centers block geometry")));
    assertTrue(candidate.warnings().stream().anyMatch(message -> message.contains("default 1x1")));
    assertEquals(-0.95D, LegacyHologramConverter.itemAnchorOffset(true, 4.0D).y(), 0.0D);
    assertEquals(0.3D, LegacyHologramConverter.itemAnchorOffset(true, 4.0D).z(), 0.0D);
    assertEquals(-1.09D - 3.5D / 16.0D * 1.5D,
        LegacyHologramConverter.itemAnchorOffset(false, 1.5D).y(), 0.000001D);
  }

  @Test
  public void supportedThirdPartySourcesScanTheirCurrentFileShapes() throws IOException {
    Path plugins = temp.newFolder("third-party").toPath();
    LegacyHologramScanner scanner = new LegacyHologramScanner();

    Path decent = plugins.resolve("DecentHolograms/holograms");
    Files.createDirectories(decent);
    Files.writeString(decent.resolve("spawn.yml"), """
        location: world:1:65:2
        display-range: 48
        pages:
          - lines:
              - content: '<green>Spawn'
        """);
    assertEquals(1, scanner.scan(LegacyImportSource.DECENT_HOLOGRAMS, decent, plugins)
        .drafts().size());

    Path displays = plugins.resolve("HolographicDisplays/database.yml");
    Files.createDirectories(displays.getParent());
    Files.writeString(displays, """
        lobby:
          position:
            world: world
            x: 4
            y: 66
            z: 8
          lines:
            - Lobby
        """);
    assertEquals(1, scanner.scan(LegacyImportSource.HOLOGRAPHIC_DISPLAYS, displays, plugins)
        .drafts().size());

    Path fancy = plugins.resolve("FancyHolograms/holograms.yml");
    Files.createDirectories(fancy.getParent());
    Files.writeString(fancy, """
        version: 2
        holograms:
          rules:
            type: TEXT
            location: {world: world, x: 5, y: 67, z: 9, yaw: 30, pitch: 5}
            visibility_distance: 72
            billboard: center
            text: ['<#00FF00>Rules']
        """);
    assertEquals(1, scanner.scan(LegacyImportSource.FANCY_HOLOGRAMS, fancy, plugins)
        .drafts().size());
  }

  @Test
  public void commandSourceArgumentsReachAllFourImporters() {
    assertEquals(LegacyImportSource.GHOLO, LegacyImportSource.parse("gholo"));
    assertEquals(LegacyImportSource.DECENT_HOLOGRAMS,
        LegacyImportSource.parse("decent-holograms"));
    assertEquals(LegacyImportSource.HOLOGRAPHIC_DISPLAYS,
        LegacyImportSource.parse("holographic-displays"));
    assertEquals(LegacyImportSource.FANCY_HOLOGRAMS,
        LegacyImportSource.parse("fancy-holograms"));
    assertEquals(List.of("gholo", "decent-holograms", "holographic-displays", "fancy-holograms"),
        LegacyImportSource.suggestions());
  }

  @Test
  public void malformedFilesAndUnknownWorldsAreReportedWithoutCandidates() throws IOException {
    Path plugins = temp.newFolder("malformed").toPath();
    Path source = plugins.resolve("GHolo/holos");
    Files.createDirectories(source);
    Files.writeString(source.resolve("bad.yml"), "Holo: [not, a, document\n");
    Files.writeString(source.resolve("unknown-world.yml"), """
        Holo:
          location: {world: missing, x: 0, y: 64, z: 0}
          rows:
            - content: Hello
        """);

    LegacyHologramScanner.LegacyScanResult scan = new LegacyHologramScanner().scan(
        LegacyImportSource.GHOLO, source, plugins);
    assertEquals(1, scan.drafts().size());
    assertEquals(1, scan.issues().size());
    LegacyHologramConverter.ConversionResult conversion = new LegacyHologramConverter().convert(
        LegacyImportSource.GHOLO, scan.drafts(), worlds());
    assertTrue(conversion.candidates().isEmpty());
    assertEquals(1, conversion.issues().size());
    assertTrue(conversion.issues().getFirst().message().contains("world is not loaded"));
  }

  @Test
  public void malformedGholoRowShapeIsRejectedInsteadOfSilentlyChanged() throws IOException {
    Path plugins = temp.newFolder("malformed-row").toPath();
    Path source = plugins.resolve("GHolo/holos");
    Files.createDirectories(source);
    Files.writeString(source.resolve("bad-row.yml"), """
        Holo:
          location: {world: world, x: 0, y: 64, z: 0}
          rows:
            - content: 42
              offset: not-a-section
        """);

    LegacyHologramScanner.LegacyScanResult scan = new LegacyHologramScanner().scan(
        LegacyImportSource.GHOLO, source, plugins);

    assertTrue(scan.drafts().isEmpty());
    assertEquals(1, scan.issues().size());
    assertTrue(scan.issues().getFirst().message().contains("content must be text"));
  }

  @Test
  public void gholoPartialScaleAndSizeMapsUseGholoDefaultsForMissingAxes() throws IOException {
    Path plugins = temp.newFolder("partial-style").toPath();
    Path source = plugins.resolve("GHolo/holos");
    Files.createDirectories(source);
    Files.writeString(source.resolve("partial.yml"), """
        Holo:
          location: {world: world, x: 0, y: 64, z: 0}
          data:
            scale: {x: 2}
            size: {width: 3}
          rows:
            - content: Hello
        """);

    LegacyHologramScanner.LegacyScanResult scan = new LegacyHologramScanner().scan(
        LegacyImportSource.GHOLO, source, plugins);
    LegacyHologramDraft.LegacyStyle style = scan.drafts().getFirst().style();

    assertEquals(2.0D, style.scaleX(), 0.0D);
    assertEquals(1.0D, style.scaleY(), 0.0D);
    assertEquals(1.0D, style.scaleZ(), 0.0D);
    assertEquals(3.0D, style.width(), 0.0D);
    assertEquals(1.0D, style.height(), 0.0D);
  }

  @Test
  public void gholoRowDefaultSentinelsInheritBaseWhilePartialVectorsRemainWhole() throws IOException {
    Path plugins = temp.newFolder("row-style-inheritance").toPath();
    Path source = plugins.resolve("GHolo/holos");
    Files.createDirectories(source);
    Files.writeString(source.resolve("inheritance.yml"), """
        Holo:
          location: {world: world, x: 0, y: 64, z: 0}
          data:
            backgroundColor: '#123456'
            textShadow: true
            billboard: fixed
            scale: {x: 2, y: 3, z: 4}
            size: {width: 7, height: 8}
          rows:
            - content: Inherit
              data:
                backgroundColor: '#000000'
                textShadow: false
                billboard: center
            - content: Override vector
              data:
                scale: {x: 5}
                size: {height: 6}
        """);

    LegacyHologramScanner.LegacyScanResult scan = new LegacyHologramScanner().scan(
        LegacyImportSource.GHOLO, source, plugins);
    LegacyHologramDraft.LegacyStyle inherited = scan.drafts().getFirst().rows().get(0).style();
    LegacyHologramDraft.LegacyStyle vector = scan.drafts().getFirst().rows().get(1).style();

    assertEquals("#123456", inherited.background());
    assertEquals(Boolean.TRUE, inherited.textShadow());
    assertEquals("fixed", inherited.billboard());
    assertEquals(2.0D, inherited.scaleX(), 0.0D);
    assertEquals(5.0D, vector.scaleX(), 0.0D);
    assertEquals(1.0D, vector.scaleY(), 0.0D);
    assertEquals(1.0D, vector.scaleZ(), 0.0D);
    assertEquals(1.0D, vector.width(), 0.0D);
    assertEquals(6.0D, vector.height(), 0.0D);
  }

  @Test
  public void symlinkSourceAndCanonicalIdConflictsAreRejected() throws IOException {
    Path plugins = temp.newFolder("security").toPath();
    Path outside = temp.newFolder("outside").toPath();
    Path link = plugins.resolve("GHolo");
    try {
      Files.createSymbolicLink(link, outside);
    } catch (UnsupportedOperationException | IOException failure) {
      Assume.assumeNoException(failure);
    }
    LegacyHologramScanner.LegacyScanResult scan = new LegacyHologramScanner().scan(
        LegacyImportSource.GHOLO, link.resolve("holos"), plugins);
    assertTrue(scan.drafts().isEmpty());
    assertEquals(LegacyImportIssue.Severity.ERROR, scan.issues().getFirst().severity());

    LegacyHologramDraft first = draft("Welcome Board", "one.yml");
    LegacyHologramDraft second = draft("welcome@board", "two.yml");
    LegacyHologramConverter.ConversionResult conversion = new LegacyHologramConverter().convert(
        LegacyImportSource.GHOLO, List.of(first, second), worlds());
    assertEquals(2, conversion.candidates().size());
    assertTrue(conversion.candidates().stream().allMatch(candidate ->
        candidate.disposition() == LegacyImportDisposition.CONFLICT));
    assertEquals("welcome-board", LegacyHologramConverter.canonicalLeaf(" Welcome Board "));
    assertFalse(conversion.issues().isEmpty());
  }

  @Test
  public void existingMenuAndBoardTargetsClassifyWithoutOverwrite() {
    LegacyHologramConverter.ConversionResult conversion = new LegacyHologramConverter().convert(
        LegacyImportSource.GHOLO, List.of(draft("Welcome", "welcome.yml")), worlds());
    LegacyImportCandidate candidate = conversion.candidates().getFirst();

    LegacyImportCandidate menuConflict = LegacyHologramImportService.classify(
        List.of(candidate), Map.of(candidate.menuId(), "different source\n"), Set.of()).getFirst();
    assertEquals(LegacyImportDisposition.CONFLICT, menuConflict.disposition());
    assertTrue(menuConflict.dispositionReason().contains("different content"));

    LegacyImportCandidate resumable = LegacyHologramImportService.classify(
        List.of(candidate), Map.of(candidate.menuId(), candidate.menuSource()), Set.of()).getFirst();
    assertEquals(LegacyImportDisposition.RESUME_BOARD, resumable.disposition());

    LegacyImportCandidate boardConflict = LegacyHologramImportService.classify(
        List.of(candidate), Map.of(), Set.of(candidate.boardId())).getFirst();
    assertEquals(LegacyImportDisposition.CONFLICT, boardConflict.disposition());
    assertTrue(boardConflict.dispositionReason().contains("board already exists"));
  }

  private static LegacyWorldCatalog worlds() {
    return LegacyWorldCatalog.of(new LegacyWorldCatalog.WorldDescriptor(
        "world", "minecraft:overworld", WORLD_UUID));
  }

  private static LegacyHologramDraft draft(String id, String identity) {
    LegacyHologramDraft.LegacyStyle style = LegacyHologramDraft.LegacyStyle.gholoDefaults();
    return new LegacyHologramDraft(id, identity,
        new LegacyHologramDraft.LegacyLocation("world", 0, 64, 0, 0, 0),
        64, null, style,
        List.of(new LegacyHologramDraft.LegacyRow("Hello", 0, 0, 0, style)), List.of());
  }

  private static JsonObject icon(JsonArray components, int index) {
    return components.get(index).getAsJsonObject().getAsJsonObject("data")
        .getAsJsonObject("icon");
  }

  private static void assertWorldOffset(MenuTransform transform, JsonArray components, int index,
                                        Vector internalOffset, Vector expected) {
    JsonArray encoded = components.get(index).getAsJsonObject().getAsJsonArray("offset");
    Vector componentOffset = new Vector(encoded.get(0).getAsDouble(), encoded.get(1).getAsDouble(),
        encoded.get(2).getAsDouble());
    Vector actual = transform.localVector(componentOffset.add(internalOffset));
    assertEquals(expected.getX(), actual.getX(), 0.000001D);
    assertEquals(expected.getY(), actual.getY(), 0.000001D);
    assertEquals(expected.getZ(), actual.getZ(), 0.000001D);
  }
}
