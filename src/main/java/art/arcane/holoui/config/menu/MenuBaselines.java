package art.arcane.holoui.config.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MenuBaselines {
  public static final String BLANK_HOLOGRAM_RESOURCE = "/baselines/blank-hologram.json";

  private static final Gson GSON = new GsonBuilder()
      .disableHtmlEscaping()
      .setPrettyPrinting()
      .create();

  private MenuBaselines() {
  }

  public static String blankHologramSource() {
    try (InputStream stream = MenuBaselines.class.getResourceAsStream(BLANK_HOLOGRAM_RESOURCE)) {
      InputStream required = Objects.requireNonNull(stream, "missing " + BLANK_HOLOGRAM_RESOURCE);
      return new String(required.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new IllegalStateException("failed to read " + BLANK_HOLOGRAM_RESOURCE, failure);
    }
  }

  public static String simpleHologramSource(String menuId, String text) {
    String id = MenuIds.require(menuId);
    String content = text == null || text.isBlank() ? "&f" + id : text.strip();

    JsonObject icon = new JsonObject();
    icon.addProperty("type", "text");
    icon.addProperty("text", content);
    JsonObject style = new JsonObject();
    style.addProperty("billboard", "vertical");
    icon.add("style", style);

    JsonObject data = new JsonObject();
    data.addProperty("type", "decoration");
    data.add("icon", icon);

    JsonObject component = new JsonObject();
    component.addProperty("id", "content");
    component.add("offset", vector(0.0D, 0.0D, 0.0D));
    component.add("data", data);

    JsonObject document = new JsonObject();
    document.add("offset", vector(0.0D, 1.7D, 0.0D));
    document.addProperty("lockPosition", false);
    document.addProperty("followPlayer", false);
    document.addProperty("closeOnDeath", true);
    document.addProperty("closeOnTeleport", true);
    JsonArray components = new JsonArray();
    components.add(component);
    document.add("components", components);
    return GSON.toJson(document) + System.lineSeparator();
  }

  private static JsonArray vector(double x, double y, double z) {
    JsonArray vector = new JsonArray();
    vector.add(x);
    vector.add(y);
    vector.add(z);
    return vector;
  }
}
