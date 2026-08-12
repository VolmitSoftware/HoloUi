package art.arcane.holoui.editor.sync;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EditorSyncSnapshotBuilderTest {
  @Test
  public void graphDiscoveryHandlesTypedNavigationAliasesCyclesAndToggleBranches() {
    Set<String> targets = new HashSet<>();
    EditorSyncSnapshotBuilder.collectTargets(JsonParser.parseString("""
        {
          "components": [{
            "data": {
              "trueActions": [
                {"type":"navigate","target":"shops/tools","mode":"push"},
                {"type":"command","source":"player","command":"/hui open menu=shops/armor"}
              ],
              "falseActions": [
                {"type":"navigate","target":"shops/tools","mode":"replace"},
                {"type":"command","source":"console","command":"holoui open admin"},
                {"type":"navigate","target":"ignored","mode":"back"}
              ]
            }
          }]
        }
        """), targets);

    assertEquals(Set.of("shops/tools", "shops/armor"), targets);
  }

  @Test
  public void fileBackedIconDiscoveryCoversStaticAndAnimatedFormsAtAnyDepth() {
    Set<String> paths = new HashSet<>();
    EditorSyncSnapshotBuilder.collectImagePaths(JsonParser.parseString("""
        {
          "components": [
            {"data":{"icon":{"type":"textImage","path":"boards/logo.png"}}},
            {"data":{"trueIcon":{"type":"animatedTextImage","source":["a.png","b.gif"]},
                     "falseIcon":{"type":"animatedTextImage","source":"single.webp"}}}
          ]
        }
        """), paths);

    assertEquals(Set.of("boards/logo.png", "a.png", "b.gif", "single.webp"), paths);
  }

  @Test
  public void imageUsageDiscoveryPreservesDuplicateRuntimeOccurrences() {
    List<String> paths = new ArrayList<>();
    EditorSyncSnapshotBuilder.collectImageUsages(JsonParser.parseString("""
        [{"type":"textImage","path":"same.png"},
          {"type":"animatedTextImage","source":["same.png","same.png"]}]
        """), paths);

    assertEquals(List.of("same.png", "same.png", "same.png"), paths);
  }

  @Test
  public void decodedImagesAreLimitedToSixtyFourPixelsPerDimension() throws Exception {
    EditorSyncSnapshotBuilder.ValidatedImage valid = EditorSyncSnapshotBuilder.validateImage(
        "valid.png", png(64, 64));
    assertEquals(4096L, valid.pixels());
    assertThrows(IllegalArgumentException.class,
        () -> EditorSyncSnapshotBuilder.validateImage("wide.png", png(65, 1)));
  }

  @Test
  public void storedAndRepeatedRuntimeImagesHaveAggregatePixelAndRowBudgets() {
    Map<String, EditorSyncSnapshotBuilder.ValidatedImage> tooManyStored = new LinkedHashMap<>();
    for (int index = 0; index < 65; index++) {
      tooManyStored.put("image-" + index + ".png",
          new EditorSyncSnapshotBuilder.ValidatedImage("image/png", 64, 64));
    }
    assertThrows(IllegalArgumentException.class, () ->
        EditorSyncSnapshotBuilder.validateImageBudgets(List.of(), tooManyStored));

    StringBuilder uses = new StringBuilder("[\n");
    for (int index = 0; index < 4097; index++) {
      if (index > 0) {
        uses.append(',');
      }
      uses.append("{\"type\":\"textImage\",\"path\":\"one.png\"}");
    }
    uses.append(']');
    Map<String, EditorSyncSnapshotBuilder.ValidatedImage> one = Map.of("one.png",
        new EditorSyncSnapshotBuilder.ValidatedImage("image/png", 1, 1));
    assertThrows(IllegalArgumentException.class, () ->
        EditorSyncSnapshotBuilder.validateImageBudgets(List.of(uses.toString()), one));
  }

  @Test
  public void storedAssetRowsDoNotConsumeTheRuntimeUsageRowBudget() {
    Map<String, EditorSyncSnapshotBuilder.ValidatedImage> stored = new LinkedHashMap<>();
    for (int index = 0; index < 512; index++) {
      stored.put("image-" + index + ".png",
          new EditorSyncSnapshotBuilder.ValidatedImage("image/png", 1, 64));
    }

    EditorSyncSnapshotBuilder.validateImageBudgets(List.of(), stored);
  }

  @Test
  public void invalidAnimatedFramesAndTraversalPathsAreNeverCollected() {
    Set<String> paths = new HashSet<>();
    EditorSyncSnapshotBuilder.collectImagePaths(JsonParser.parseString("""
        [{"type":"animatedTextImage","source":["ok.png","../escape.png",null,""]},
         {"type":"textImage","path":".hidden.png"}]
        """), paths);

    assertEquals(Set.of("ok.png"), paths);
  }

  @Test
  public void mediaDetectionUsesMagicAndRejectsArbitraryBytes() {
    assertEquals("image/png", EditorSyncSnapshotBuilder.detectMediaType(
        new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a}));
    assertEquals("image/jpeg", EditorSyncSnapshotBuilder.detectMediaType(
        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
    assertEquals("image/gif", EditorSyncSnapshotBuilder.detectMediaType(
        "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    assertEquals("image/webp", EditorSyncSnapshotBuilder.detectMediaType(
        "RIFF0000WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    assertEquals("image/bmp", EditorSyncSnapshotBuilder.detectMediaType(new byte[]{'B', 'M'}));
    assertThrows(IllegalArgumentException.class,
        () -> EditorSyncSnapshotBuilder.detectMediaType(new byte[]{1, 2, 3}));
  }

  @Test
  public void confinedResolutionRejectsSymbolicAncestors() throws Exception {
    Path root = Files.createTempDirectory("holoui-sync-images");
    Path outside = Files.createTempDirectory("holoui-sync-outside");
    try {
      Path link = root.resolve("linked");
      try {
        Files.createSymbolicLink(link, outside);
      } catch (UnsupportedOperationException | java.io.IOException failure) {
        org.junit.Assume.assumeNoException(failure);
      }
      assertThrows(java.io.IOException.class,
          () -> EditorSyncSnapshotBuilder.resolveConfinedFile(root, "linked/image.png", false));
      assertTrue(Files.notExists(outside.resolve("image.png")));
    } finally {
      Files.deleteIfExists(root.resolve("linked"));
      Files.deleteIfExists(root);
      Files.deleteIfExists(outside);
    }
  }

  private static byte[] png(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", output);
    return output.toByteArray();
  }
}
