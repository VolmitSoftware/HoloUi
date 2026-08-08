package art.arcane.holoui.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
  public void menuSchemaIncludesRuntimeCloseOnTeleportField() throws IOException {
    JsonObject properties = schema().getAsJsonObject("properties");

    assertEquals("boolean", properties.getAsJsonObject("closeOnTeleport").get("type").getAsString());
  }

  private static JsonObject schema() throws IOException {
    Path path = Path.of(System.getProperty("user.dir"), "schema", "holoui.schema.json");
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static List<String> required(JsonObject definition) {
    JsonArray values = definition.getAsJsonArray("required");
    return values.asList().stream().map(value -> value.getAsString()).toList();
  }
}
