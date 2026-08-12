package art.arcane.holoui.config.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class MenuRowMutationsTest {
  @Test
  public void addAndInsertCreateCollisionFreeTextDecorationRows() {
    JsonObject document = document();

    MenuRowMutations.addTextRow(document, "First");
    MenuRowMutations.addTextRow(document, "Second");
    MenuRowMutations.insertTextRow(document, 2, "Middle");

    JsonArray rows = document.getAsJsonArray("components");
    assertEquals(4, rows.size());
    assertEquals("existing", rows.get(0).getAsJsonObject().get("id").getAsString());
    assertEquals("row-3", rows.get(1).getAsJsonObject().get("id").getAsString());
    assertEquals("Middle", text(rows, 1));
    assertEquals("row-1", rows.get(2).getAsJsonObject().get("id").getAsString());
    assertEquals("row-2", rows.get(3).getAsJsonObject().get("id").getAsString());
    assertEquals(-0.125D, rows.get(1).getAsJsonObject().getAsJsonArray("offset").get(1).getAsDouble(), 0.0D);
  }

  @Test
  public void setPreservesUnknownComponentAndTextIconFields() {
    JsonObject document = document();
    JsonObject row = document.getAsJsonArray("components").get(0).getAsJsonObject();
    row.addProperty("extension", "keep");
    JsonObject icon = row.getAsJsonObject("data").getAsJsonObject("icon");
    icon.addProperty("refreshTicks", 20);

    MenuRowMutations.setTextRow(document, 1, "Changed");

    assertEquals("keep", row.get("extension").getAsString());
    assertEquals(20, icon.get("refreshTicks").getAsInt());
    assertEquals("Changed", icon.get("text").getAsString());
  }

  @Test
  public void removeAndRelativeOffsetUseOneBasedRows() {
    JsonObject document = document();
    MenuRowMutations.addTextRow(document, "Second");

    MenuRowMutations.offsetRow(document, 2, "~1.5", "~", "-2");
    JsonArray offset = document.getAsJsonArray("components").get(1).getAsJsonObject()
        .getAsJsonArray("offset");
    assertEquals(1.5D, offset.get(0).getAsDouble(), 0.0D);
    assertEquals(-0.25D, offset.get(1).getAsDouble(), 0.0D);
    assertEquals(-2.0D, offset.get(2).getAsDouble(), 0.0D);

    MenuRowMutations.removeRow(document, 1);
    assertEquals(1, document.getAsJsonArray("components").size());
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.removeRow(document, 0));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.offsetRow(document, 1, "NaN", "0", "0"));
  }

  @Test
  public void setRejectsAmbiguousToggleRows() {
    JsonObject document = JsonParser.parseString("""
        {"components":[{"id":"toggle","offset":[0,0,0],"data":{"type":"toggle"}}]}
        """).getAsJsonObject();

    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setTextRow(document, 1, "ambiguous"));
  }

  @Test
  public void singleComponentObjectNormalizesAndNonTextIconIsReplaced() {
    JsonObject document = JsonParser.parseString("""
        {
          "components": {
            "id": "single",
            "offset": [0, 0, 0],
            "data": {
              "type": "button",
              "icon": {"type": "item", "item": "STONE", "extension": true}
            }
          }
        }
        """).getAsJsonObject();

    MenuRowMutations.setTextRow(document, 1, "Text");

    JsonObject icon = document.getAsJsonArray("components").get(0).getAsJsonObject()
        .getAsJsonObject("data").getAsJsonObject("icon");
    assertEquals("text", icon.get("type").getAsString());
    assertEquals("Text", icon.get("text").getAsString());
    assertFalse(icon.has("extension"));
  }

  @Test
  public void textLengthAndCoordinateBoundsAreEnforced() {
    JsonObject document = document();

    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.addTextRow(document, "x".repeat(MenuRowMutations.MAX_TEXT_LENGTH + 1)));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.offsetRow(document, 1, "60000001", "0", "0"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.offsetRow(document, 1, "~Infinity", "0", "0"));
  }

  @Test
  public void iconMutationCoversEveryAuthorableIconAndPreservesCompatibleStyle() {
    JsonObject document = document();
    JsonObject originalIcon = icon(document);
    JsonObject style = new JsonObject();
    style.addProperty("billboard", "center");
    originalIcon.add("style", style);

    MenuRowMutations.setIcon(document, 1, "image", "boards/logo.png");
    assertEquals("textImage", icon(document).get("type").getAsString());
    assertEquals("boards/logo.png", icon(document).get("path").getAsString());
    assertEquals("center", icon(document).getAsJsonObject("style").get("billboard").getAsString());

    MenuRowMutations.setIcon(document, 1, "animated", "one.png, two.png");
    assertEquals("animatedTextImage", icon(document).get("type").getAsString());
    assertEquals(2, icon(document).getAsJsonArray("source").size());

    MenuRowMutations.setIcon(document, 1, "item", "Minecraft:Diamond");
    assertEquals("minecraft:diamond", icon(document).get("item").getAsString());
    assertEquals(1, icon(document).get("count").getAsInt());

    MenuRowMutations.setIcon(document, 1, "block", "Minecraft:Stone");
    assertEquals("minecraft:stone", icon(document).get("block").getAsString());

    MenuRowMutations.setIcon(document, 1, "custom_item", "oraxen@ruby");
    assertEquals("customItem", icon(document).get("type").getAsString());
    assertEquals("oraxen", icon(document).get("provider").getAsString());
    assertEquals("ruby", icon(document).get("item").getAsString());

    MenuRowMutations.setIcon(document, 1, "entity", "Minecraft:Parrot");
    assertEquals("minecraft:parrot", icon(document).get("entity").getAsString());
    assertFalse(icon(document).has("style"));

    MenuRowMutations.setIcon(document, 1, "text", "A new label");
    assertEquals("A new label", icon(document).get("text").getAsString());
  }

  @Test
  public void styleMutationValidatesValuesSupportsResetAndRejectsEntityStyle() {
    JsonObject document = document();

    MenuRowMutations.setStyle(document, 1, "billboard", "vertical");
    MenuRowMutations.setStyle(document, 1, "text_shadow", "true");
    MenuRowMutations.setStyle(document, 1, "background", "#80402010");
    MenuRowMutations.setStyle(document, 1, "brightness", "12");
    MenuRowMutations.setStyle(document, 1, "scale", "2.5");
    JsonObject style = icon(document).getAsJsonObject("style");
    assertEquals("vertical", style.get("billboard").getAsString());
    assertTrue(style.get("shadow").getAsBoolean());
    assertEquals("#80402010", style.get("backgroundArgb").getAsString());
    assertEquals(12, style.get("blockLight").getAsInt());
    assertEquals(12, style.get("skyLight").getAsInt());
    assertEquals(2.5D, style.get("scaleX").getAsDouble(), 0.0D);
    assertEquals(2.5D, style.get("scaleY").getAsDouble(), 0.0D);
    assertEquals(2.5D, style.get("scaleZ").getAsDouble(), 0.0D);

    MenuRowMutations.setStyle(document, 1, "brightness", "*");
    assertFalse(style.has("blockLight"));
    assertFalse(style.has("skyLight"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setStyle(document, 1, "opacity", "256"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setStyle(document, 1, "shadow", "yes"));

    MenuRowMutations.setIcon(document, 1, "entity", "minecraft:parrot");
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setStyle(document, 1, "scale", "2"));
  }

  @Test
  public void imageReplacementCreatesOneCenteredDecorationAndKeepsRootExtensions() {
    JsonObject document = document();

    MenuRowMutations.replaceWithImage(document, "maps/spawn.png");

    assertTrue(document.getAsJsonObject("unknownRoot").get("keep").getAsBoolean());
    JsonArray components = document.getAsJsonArray("components");
    assertEquals(1, components.size());
    JsonObject component = components.get(0).getAsJsonObject();
    assertEquals("image", component.get("id").getAsString());
    assertEquals("textImage", icon(document).get("type").getAsString());
    assertEquals("maps/spawn.png", icon(document).get("path").getAsString());
  }

  @Test
  public void iconAndStyleMutationsRejectAmbiguousRowsAndMalformedValues() {
    JsonObject document = JsonParser.parseString("""
        {"components":[{"id":"toggle","offset":[0,0,0],"data":{"type":"toggle"}}]}
        """).getAsJsonObject();

    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setIcon(document, 1, "text", "value"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setStyle(document, 1, "scale", "2"));

    JsonObject normal = document();
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setIcon(normal, 1, "customItem", "missing-separator"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setIcon(normal, 1, "animated", "one.png,"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setStyle(normal, 1, "glow", "#fff"));
    assertThrows(IllegalArgumentException.class,
        () -> MenuRowMutations.setStyle(normal, 1, "unknown", "value"));
  }

  private static JsonObject document() {
    return JsonParser.parseString("""
        {
          "offset": [0, 1, 2],
          "unknownRoot": {"keep": true},
          "components": [{
            "id": "existing",
            "offset": [0, 0, 0],
            "data": {
              "type": "decoration",
              "icon": {"type": "text", "text": "Original"}
            }
          }]
        }
        """).getAsJsonObject();
  }

  private static String text(JsonArray rows, int index) {
    return rows.get(index).getAsJsonObject().getAsJsonObject("data")
        .getAsJsonObject("icon").get("text").getAsString();
  }

  private static JsonObject icon(JsonObject document) {
    return document.getAsJsonArray("components").get(0).getAsJsonObject()
        .getAsJsonObject("data").getAsJsonObject("icon");
  }
}
