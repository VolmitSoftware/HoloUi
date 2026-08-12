package art.arcane.holoui.config.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MenuRowMutations {
  public static final int MAX_TEXT_LENGTH = 8192;
  public static final double ROW_SPACING = 0.25D;

  private static final double MAX_COORDINATE = 6.0E7D;

  private MenuRowMutations() {
  }

  public static JsonObject addTextRow(JsonObject document, String text) {
    JsonArray components = components(document);
    return insertTextRow(document, components.size() + 1, text);
  }

  public static JsonObject insertTextRow(JsonObject document, int row, String text) {
    JsonArray components = components(document);
    int index = insertionIndex(row, components.size());
    JsonArray offset = insertionOffset(components, index);
    components.add(new JsonObject());
    for (int current = components.size() - 1; current > index; current--) {
      components.set(current, components.get(current - 1));
    }
    components.set(index, textRow(nextRowId(components), requireText(text), offset));
    return document;
  }

  public static JsonObject setTextRow(JsonObject document, int row, String text) {
    JsonObject component = component(components(document), row);
    JsonObject data = requiredObject(component, "data", "row " + row + " data");
    String componentType = string(data.get("type"));
    if (!"button".equals(componentType) && !"decoration".equals(componentType)) {
      throw new IllegalArgumentException(
          "row " + row + " is " + fallback(componentType) + "; only button and decoration rows have one editable icon");
    }
    String requiredText = requireText(text);
    JsonElement iconElement = data.get("icon");
    if (iconElement != null && iconElement.isJsonObject()
        && "text".equals(string(iconElement.getAsJsonObject().get("type")))) {
      iconElement.getAsJsonObject().addProperty("text", requiredText);
      return document;
    }
    JsonObject icon = new JsonObject();
    icon.addProperty("type", "text");
    icon.addProperty("text", requiredText);
    data.add("icon", icon);
    return document;
  }

  public static JsonObject removeRow(JsonObject document, int row) {
    JsonArray components = components(document);
    int index = existingIndex(row, components.size());
    components.remove(index);
    return document;
  }

  public static JsonObject offsetRow(JsonObject document, int row, String x, String y, String z) {
    JsonObject component = component(components(document), row);
    double[] current = offset(component.get("offset"), row);
    JsonArray changed = new JsonArray();
    changed.add(coordinate(x, current[0], "x"));
    changed.add(coordinate(y, current[1], "y"));
    changed.add(coordinate(z, current[2], "z"));
    component.add("offset", changed);
    return document;
  }

  public static JsonObject setIcon(JsonObject document, int row, String type, String value) {
    JsonObject data = rowData(document, row);
    JsonObject previous = optionalObject(data.get("icon"));
    JsonObject icon = icon(type, value);
    if (!"entity".equals(icon.get("type").getAsString()) && previous != null
        && previous.has("style")) {
      icon.add("style", previous.get("style").deepCopy());
    }
    data.add("icon", icon);
    return document;
  }

  public static JsonObject setStyle(JsonObject document, int row, String property, String value) {
    JsonObject data = rowData(document, row);
    JsonObject icon = requiredObject(data, "icon", "row " + row + " icon");
    if ("entity".equals(string(icon.get("type")))) {
      throw new IllegalArgumentException("entity icons do not support display style");
    }
    String key = requireStyleProperty(property);
    JsonObject style = optionalObject(icon.get("style"));
    if (style == null) {
      style = new JsonObject();
      icon.add("style", style);
    }
    if ("*".equals(value)) {
      clearStyle(style, key);
    } else {
      applyStyle(style, key, value);
    }
    if (style.isEmpty()) {
      icon.remove("style");
    }
    return document;
  }

  public static JsonObject replaceWithImage(JsonObject document, String path) {
    JsonObject icon = icon("image", path);
    JsonObject data = new JsonObject();
    data.addProperty("type", "decoration");
    data.add("icon", icon);
    JsonObject component = new JsonObject();
    component.addProperty("id", "image");
    component.add("offset", vector(0.0D, 0.0D, 0.0D));
    component.add("data", data);
    JsonArray replacement = new JsonArray();
    replacement.add(component);
    document.add("components", replacement);
    return document;
  }

  private static JsonArray components(JsonObject document) {
    if (document == null) {
      throw new IllegalArgumentException("menu document must not be null");
    }
    JsonElement existing = document.get("components");
    if (existing == null || existing.isJsonNull()) {
      JsonArray created = new JsonArray();
      document.add("components", created);
      return created;
    }
    if (existing.isJsonArray()) {
      return existing.getAsJsonArray();
    }
    if (existing.isJsonObject()) {
      JsonArray normalized = new JsonArray();
      normalized.add(existing);
      document.add("components", normalized);
      return normalized;
    }
    throw new IllegalArgumentException("menu components must be an array or component object");
  }

  private static JsonObject component(JsonArray components, int row) {
    JsonElement element = components.get(existingIndex(row, components.size()));
    if (!element.isJsonObject()) {
      throw new IllegalArgumentException("row " + row + " must be a component object");
    }
    return element.getAsJsonObject();
  }

  private static JsonObject rowData(JsonObject document, int row) {
    JsonObject component = component(components(document), row);
    JsonObject data = requiredObject(component, "data", "row " + row + " data");
    String componentType = string(data.get("type"));
    if (!"button".equals(componentType) && !"decoration".equals(componentType)) {
      throw new IllegalArgumentException(
          "row " + row + " is " + fallback(componentType) + "; only button and decoration rows have one editable icon");
    }
    return data;
  }

  private static JsonObject icon(String type, String value) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("icon type must not be blank");
    }
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("icon value must not be blank");
    }
    String normalizedType = type.strip().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    String normalizedValue = value.strip();
    JsonObject icon = new JsonObject();
    switch (normalizedType) {
      case "text" -> {
        icon.addProperty("type", "text");
        icon.addProperty("text", requireText(value));
      }
      case "image", "textimage" -> {
        icon.addProperty("type", "textImage");
        icon.addProperty("path", normalizedValue);
      }
      case "animated", "animatedimage", "animatedtextimage" -> {
        String[] rawFrames = normalizedValue.split(",", -1);
        JsonArray frames = new JsonArray();
        for (String rawFrame : rawFrames) {
          String frame = rawFrame.strip();
          if (frame.isEmpty()) {
            throw new IllegalArgumentException("animated image frames must not be blank");
          }
          frames.add(frame);
        }
        icon.addProperty("type", "animatedTextImage");
        icon.add("source", frames);
        icon.addProperty("speed", 5);
      }
      case "item" -> {
        icon.addProperty("type", "item");
        icon.addProperty("item", normalizedValue.toLowerCase(Locale.ROOT));
        icon.addProperty("count", 1);
      }
      case "block" -> {
        icon.addProperty("type", "block");
        icon.addProperty("block", normalizedValue.toLowerCase(Locale.ROOT));
      }
      case "entity" -> {
        icon.addProperty("type", "entity");
        icon.addProperty("entity", normalizedValue.toLowerCase(Locale.ROOT));
      }
      case "customitem" -> {
        int separator = normalizedValue.indexOf('@');
        if (separator < 1 || separator == normalizedValue.length() - 1) {
          throw new IllegalArgumentException("custom-item values must use provider@item");
        }
        icon.addProperty("type", "customItem");
        icon.addProperty("provider", normalizedValue.substring(0, separator));
        icon.addProperty("item", normalizedValue.substring(separator + 1));
        icon.addProperty("count", 1);
      }
      default -> throw new IllegalArgumentException(
          "icon type must be text, image, animated, item, block, customItem, or entity");
    }
    return icon;
  }

  private static String requireStyleProperty(String property) {
    if (property == null || property.isBlank()) {
      throw new IllegalArgumentException("style property must not be blank");
    }
    return switch (property.strip().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
      case "billboard" -> "billboard";
      case "shadow", "textshadow" -> "shadow";
      case "seethrough" -> "seeThrough";
      case "alignment", "textalignment" -> "textAlignment";
      case "background", "backgroundargb", "backgroundcolor" -> "backgroundArgb";
      case "opacity", "textopacity" -> "textOpacity";
      case "linewidth" -> "lineWidth";
      case "brightness" -> "brightness";
      case "viewrange" -> "viewRange";
      case "shadowradius" -> "shadowRadius";
      case "shadowstrength" -> "shadowStrength";
      case "width", "cullingwidth" -> "cullingWidth";
      case "height", "cullingheight" -> "cullingHeight";
      case "glow", "glowcolor" -> "glowColor";
      case "scale" -> "scale";
      case "scalex" -> "scaleX";
      case "scaley" -> "scaleY";
      case "scalez" -> "scaleZ";
      default -> throw new IllegalArgumentException("unknown display style property: " + property);
    };
  }

  private static void applyStyle(JsonObject style, String key, String value) {
    switch (key) {
      case "billboard" -> style.addProperty(key, enumeration(value, key, Set.of("fixed", "vertical", "horizontal", "center")));
      case "shadow", "seeThrough" -> style.addProperty(key, bool(value, key));
      case "textAlignment" -> style.addProperty(key, enumeration(value, key, Set.of("left", "center", "right")));
      case "backgroundArgb", "glowColor" -> style.addProperty(key, argb(value, key));
      case "textOpacity" -> style.addProperty(key, integer(value, 0, 255, key));
      case "lineWidth" -> style.addProperty(key, integer(value, 1, 16384, key));
      case "brightness" -> {
        int brightness = integer(value, 0, 15, key);
        style.addProperty("blockLight", brightness);
        style.addProperty("skyLight", brightness);
      }
      case "viewRange" -> style.addProperty(key, decimal(value, 0.01D, 64.0D, key));
      case "shadowRadius" -> style.addProperty(key, decimal(value, 0.0D, 64.0D, key));
      case "shadowStrength" -> style.addProperty(key, decimal(value, 0.0D, 1.0D, key));
      case "cullingWidth", "cullingHeight" -> style.addProperty(key, decimal(value, 0.0D, 4096.0D, key));
      case "scale" -> {
        double scale = decimal(value, 0.01D, 64.0D, key);
        style.addProperty("scaleX", scale);
        style.addProperty("scaleY", scale);
        style.addProperty("scaleZ", scale);
      }
      case "scaleX", "scaleY", "scaleZ" -> style.addProperty(key, decimal(value, 0.01D, 64.0D, key));
      default -> throw new IllegalArgumentException("unknown display style property: " + key);
    }
  }

  private static void clearStyle(JsonObject style, String key) {
    if ("brightness".equals(key)) {
      style.remove("blockLight");
      style.remove("skyLight");
      return;
    }
    if ("scale".equals(key)) {
      style.remove("scaleX");
      style.remove("scaleY");
      style.remove("scaleZ");
      return;
    }
    style.remove(key);
  }

  private static String enumeration(String value, String label, Set<String> allowed) {
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    if (!allowed.contains(normalized)) {
      throw new IllegalArgumentException(label + " must be one of " + String.join(", ", allowed));
    }
    return normalized;
  }

  private static boolean bool(String value, String label) {
    if (value.equalsIgnoreCase("true")) {
      return true;
    }
    if (value.equalsIgnoreCase("false")) {
      return false;
    }
    throw new IllegalArgumentException(label + " must be true or false");
  }

  private static int integer(String value, int minimum, int maximum, String label) {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < minimum || parsed > maximum) {
        throw new IllegalArgumentException(label + " must be between " + minimum + " and " + maximum);
      }
      return parsed;
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(label + " must be an integer");
    }
  }

  private static double decimal(String value, double minimum, double maximum, String label) {
    try {
      double parsed = Double.parseDouble(value);
      if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
        throw new IllegalArgumentException(label + " must be finite and between " + minimum + " and " + maximum);
      }
      return parsed;
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(label + " must be a number");
    }
  }

  private static String argb(String value, String label) {
    if (!value.matches("#[0-9A-Fa-f]{8}")) {
      throw new IllegalArgumentException(label + " must use #AARRGGBB");
    }
    return value.toUpperCase(Locale.ROOT);
  }

  private static JsonObject optionalObject(JsonElement element) {
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
  }

  private static int existingIndex(int row, int size) {
    if (row < 1 || row > size) {
      throw new IllegalArgumentException("row must be between 1 and " + size);
    }
    return row - 1;
  }

  private static int insertionIndex(int row, int size) {
    if (row < 1 || row > size + 1) {
      throw new IllegalArgumentException("insert row must be between 1 and " + (size + 1));
    }
    return row - 1;
  }

  private static JsonArray insertionOffset(JsonArray components, int index) {
    if (components.isEmpty()) {
      return vector(0.0D, 0.0D, 0.0D);
    }
    double[] previous = index > 0 ? optionalOffset(components.get(index - 1)) : null;
    double[] next = index < components.size() ? optionalOffset(components.get(index)) : null;
    if (previous != null && next != null) {
      return vector(
          (previous[0] + next[0]) / 2.0D,
          (previous[1] + next[1]) / 2.0D,
          (previous[2] + next[2]) / 2.0D
      );
    }
    if (previous != null) {
      return vector(previous[0], previous[1] - ROW_SPACING, previous[2]);
    }
    if (next != null) {
      return vector(next[0], next[1] + ROW_SPACING, next[2]);
    }
    return vector(0.0D, -index * ROW_SPACING, 0.0D);
  }

  private static double[] optionalOffset(JsonElement component) {
    if (component == null || !component.isJsonObject()) {
      return null;
    }
    try {
      return offset(component.getAsJsonObject().get("offset"), -1);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static double[] offset(JsonElement element, int row) {
    if (element == null || element.isJsonNull()) {
      return new double[]{0.0D, 0.0D, 0.0D};
    }
    if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
      throw new IllegalArgumentException(prefix(row) + "offset must contain exactly three numbers");
    }
    JsonArray array = element.getAsJsonArray();
    double[] values = new double[3];
    for (int index = 0; index < values.length; index++) {
      JsonElement value = array.get(index);
      if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException(prefix(row) + "offset must contain exactly three numbers");
      }
      values[index] = value.getAsDouble();
      requireCoordinate(values[index], "stored offset");
    }
    return values;
  }

  private static JsonObject textRow(String id, String text, JsonArray offset) {
    JsonObject icon = new JsonObject();
    icon.addProperty("type", "text");
    icon.addProperty("text", text);
    JsonObject data = new JsonObject();
    data.addProperty("type", "decoration");
    data.add("icon", icon);
    JsonObject component = new JsonObject();
    component.addProperty("id", id);
    component.add("offset", offset);
    component.add("data", data);
    return component;
  }

  private static String nextRowId(JsonArray components) {
    Set<String> ids = new HashSet<>();
    for (JsonElement component : components) {
      if (!component.isJsonObject()) {
        continue;
      }
      String id = string(component.getAsJsonObject().get("id"));
      if (id != null) {
        ids.add(id);
      }
    }
    int suffix = 1;
    while (ids.contains("row-" + suffix)) {
      suffix++;
    }
    return "row-" + suffix;
  }

  private static JsonObject requiredObject(JsonObject parent, String key, String label) {
    JsonElement value = parent.get(key);
    if (value == null || !value.isJsonObject()) {
      throw new IllegalArgumentException(label + " must be an object");
    }
    return value.getAsJsonObject();
  }

  private static double coordinate(String expression, double current, String axis) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException(axis + " offset must not be blank");
    }
    String value = expression.strip();
    double changed;
    try {
      if (value.equals("~")) {
        changed = current;
      } else if (value.startsWith("~")) {
        changed = current + Double.parseDouble(value.substring(1));
      } else {
        changed = Double.parseDouble(value);
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(axis + " offset must be a number, ~, or ~number");
    }
    return requireCoordinate(changed, axis + " offset");
  }

  private static double requireCoordinate(double value, String label) {
    if (!Double.isFinite(value) || Math.abs(value) > MAX_COORDINATE) {
      throw new IllegalArgumentException(label + " must be finite and between -" + MAX_COORDINATE
          + " and " + MAX_COORDINATE);
    }
    return value;
  }

  private static String requireText(String text) {
    if (text == null) {
      throw new IllegalArgumentException("row text must not be null");
    }
    if (text.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException("row text must be at most " + MAX_TEXT_LENGTH + " characters");
    }
    return text;
  }

  private static JsonArray vector(double x, double y, double z) {
    JsonArray vector = new JsonArray();
    vector.add(new JsonPrimitive(x));
    vector.add(new JsonPrimitive(y));
    vector.add(new JsonPrimitive(z));
    return vector;
  }

  private static String string(JsonElement element) {
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
        ? element.getAsString()
        : null;
  }

  private static String fallback(String value) {
    return value == null ? "an unknown component type" : value;
  }

  private static String prefix(int row) {
    return row < 1 ? "" : "row " + row + " ";
  }
}
