package art.arcane.holoui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HoloUiSchemaContractTest {

  @Test
  public void iconConstraintsMatchRuntimeAcceptedFields() throws IOException {
    JsonObject definitions = schema().getAsJsonObject("$defs");
    JsonObject imagePath = definitions.getAsJsonObject("textImageIcon").getAsJsonObject("properties").getAsJsonObject("path");
    JsonObject animated = definitions.getAsJsonObject("animatedTextImageIcon");
    JsonObject item = definitions.getAsJsonObject("itemIcon").getAsJsonObject("properties");

    assertEquals("string", imagePath.get("type").getAsString());
    assertTrue(imagePath.has("pattern"));
    assertEquals("integer", item.getAsJsonObject("count").get("type").getAsString());
    assertFalse(item.getAsJsonObject("customModelValue").has("minimum"));
    assertFalse(required(animated).contains("speed"));
    assertFalse(animated.getAsJsonObject("properties").getAsJsonObject("speed").has("minimum"));
  }

  @Test
  public void iconSchemaIncludesTheValidatedDisplayStyleContract() throws IOException {
    JsonObject definitions = schema().getAsJsonObject("$defs");
    JsonObject icon = definitions.getAsJsonObject("icon");
    JsonObject style = definitions.getAsJsonObject("iconDisplayStyle");
    JsonObject properties = style.getAsJsonObject("properties");
    JsonArray styleVariants = icon.getAsJsonObject("properties").getAsJsonObject("style").getAsJsonArray("oneOf");

    assertEquals("#/$defs/iconDisplayStyle",
        styleVariants.get(0).getAsJsonObject().get("$ref").getAsString());
    assertEquals("null", styleVariants.get(1).getAsJsonObject().get("type").getAsString());
    assertEquals(List.of("fixed", "vertical", "horizontal", "center"),
        properties.getAsJsonObject("billboard").getAsJsonArray("enum").asList().stream()
            .map(value -> value.getAsString()).toList());
    assertEquals(255, properties.getAsJsonObject("textOpacity").get("maximum").getAsInt());
    assertEquals(15, properties.getAsJsonObject("blockLight").get("maximum").getAsInt());
    assertEquals(0.01D, properties.getAsJsonObject("scaleX").get("minimum").getAsDouble(), 0D);
    assertTrue(style.getAsJsonObject("dependentRequired").has("blockLight"));
    assertFalse(required(icon).contains("style"));
  }

  @Test
  public void iconSchemaIncludesThePacketEntityContract() throws IOException {
    JsonObject definitions = schema().getAsJsonObject("$defs");
    JsonObject icon = definitions.getAsJsonObject("icon");
    JsonObject entity = definitions.getAsJsonObject("entityIcon");
    JsonObject properties = entity.getAsJsonObject("properties");
    JsonArray iconTypes = icon.getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");

    assertTrue(iconTypes.asList().stream().anyMatch(value -> "entity".equals(value.getAsString())));
    assertEquals(List.of("entity"), required(entity));
    assertEquals(0D, properties.getAsJsonObject("width").get("exclusiveMinimum").getAsDouble(), 0D);
    assertEquals(64D, properties.getAsJsonObject("width").get("maximum").getAsDouble(), 0D);
    assertEquals(1D, properties.getAsJsonObject("width").get("default").getAsDouble(), 0D);
    assertEquals(64D, properties.getAsJsonObject("height").get("maximum").getAsDouble(), 0D);
  }

  @Test
  public void iconSchemaIncludesTheBlockDisplayContract() throws IOException {
    JsonObject definitions = schema().getAsJsonObject("$defs");
    JsonObject icon = definitions.getAsJsonObject("icon");
    JsonObject block = definitions.getAsJsonObject("blockIcon");
    JsonArray iconTypes = icon.getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");

    assertTrue(iconTypes.asList().stream().anyMatch(value -> "block".equals(value.getAsString())));
    assertEquals(List.of("block"), required(block));
    assertEquals("^([a-z0-9_.-]+:)?[a-z0-9_./-]+$",
        block.getAsJsonObject("properties").getAsJsonObject("block").get("pattern").getAsString());
  }

  @Test
  public void previewElementSchemaRequiresFieldsForEachElementType() throws IOException {
    JsonObject element = previewSchema().getAsJsonObject("$defs").getAsJsonObject("element");
    JsonArray variants = element.getAsJsonArray("allOf");
    Map<String, List<String>> requiredByType = new HashMap<>();
    for (JsonElement variantElement : variants) {
      JsonObject variant = variantElement.getAsJsonObject();
      String type = variant.getAsJsonObject("if").getAsJsonObject("properties")
          .getAsJsonObject("type").get("const").getAsString();
      requiredByType.put(type, required(variant.getAsJsonObject("then")));
    }

    assertEquals(List.of("width", "height", "color"), requiredByType.get("panel"));
    assertEquals(List.of("size", "color"), requiredByType.get("cell"));
    assertEquals(List.of("size", "index"), requiredByType.get("slot"));
    assertEquals(List.of("text"), requiredByType.get("label"));
  }

  @Test
  public void menuSchemaIncludesRuntimeCloseOnTeleportField() throws IOException {
    JsonObject properties = schema().getAsJsonObject("properties");

    assertEquals("boolean", properties.getAsJsonObject("closeOnTeleport").get("type").getAsString());
  }

  @Test
  public void actionSchemaIncludesNativeNavigationModes() throws IOException {
    JsonObject definitions = schema().getAsJsonObject("$defs");
    JsonObject action = definitions.getAsJsonObject("action");
    JsonArray actionTypes = action.getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");
    JsonObject navigation = definitions.getAsJsonObject("navigationAction");
    JsonArray modes = navigation.getAsJsonObject("properties").getAsJsonObject("mode").getAsJsonArray("enum");

    assertTrue(actionTypes.asList().stream().anyMatch(value -> "navigate".equals(value.getAsString())));
    assertEquals(List.of("push", "replace", "back", "home", "close"),
        modes.asList().stream().map(value -> value.getAsString()).toList());
  }

  @Test
  public void actionSchemaIncludesTypedInteractionActions() throws IOException {
    JsonObject definitions = schema().getAsJsonObject("$defs");
    JsonArray actionTypes = definitions.getAsJsonObject("action")
        .getAsJsonObject("properties").getAsJsonObject("type").getAsJsonArray("enum");

    assertEquals(List.of("command", "sound", "message", "teleport", "connect", "navigate"),
        actionTypes.asList().stream().map(value -> value.getAsString()).toList());
    assertEquals(List.of("message"), required(definitions.getAsJsonObject("messageAction")));
    assertEquals(List.of("world", "x", "y", "z", "yaw", "pitch"),
        required(definitions.getAsJsonObject("teleportAction")));
    assertEquals(List.of("server"), required(definitions.getAsJsonObject("connectAction")));
  }

  @Test
  public void actionSchemaIncludesOptionalValidatedClickTriggers() throws IOException {
    JsonObject action = schema().getAsJsonObject("$defs").getAsJsonObject("action");
    JsonObject trigger = action.getAsJsonObject("properties").getAsJsonObject("trigger");
    JsonArray variants = trigger.getAsJsonArray("oneOf");

    assertEquals(List.of("any", "left_click", "right_click", "shift_left_click", "shift_right_click"),
        variants.get(0).getAsJsonObject().getAsJsonArray("enum").asList().stream()
            .map(value -> value.getAsString()).toList());
    assertEquals("null", variants.get(1).getAsJsonObject().get("type").getAsString());
    assertEquals("any", trigger.get("default").getAsString());
    assertFalse(required(action).contains("trigger"));
  }

  private static JsonObject schema() throws IOException {
    Path path = Path.of(System.getProperty("user.dir"), "schema", "holoui.schema.json");
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static JsonObject previewSchema() throws IOException {
    Path path = Path.of(System.getProperty("user.dir"), "schema", "holoui-preview.schema.json");
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static List<String> required(JsonObject definition) {
    JsonArray values = definition.getAsJsonArray("required");
    return values.asList().stream().map(value -> value.getAsString()).toList();
  }
}
