package art.arcane.holoui.editor.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class EditorSyncJson {
  public static final int PROTOCOL_VERSION = 1;
  public static final String PROJECT_FORMAT = "holoui-sync-project";
  public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

  private EditorSyncJson() {
  }

  public static String revision(JsonObject project) {
    JsonObject content = project.deepCopy();
    content.remove("baseRevision");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return "sha256:" + HexFormat.of().formatHex(
          digest.digest(canonical(content).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public static String canonical(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return "null";
    }
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      StringBuilder output = new StringBuilder("[");
      for (int index = 0; index < array.size(); index++) {
        if (index > 0) {
          output.append(',');
        }
        output.append(canonical(array.get(index)));
      }
      return output.append(']').toString();
    }
    if (element.isJsonObject()) {
      JsonObject object = element.getAsJsonObject();
      List<String> names = new ArrayList<>(object.keySet());
      names.sort(String::compareTo);
      StringBuilder output = new StringBuilder("{");
      for (int index = 0; index < names.size(); index++) {
        if (index > 0) {
          output.append(',');
        }
        String name = names.get(index);
        output.append(quoted(name)).append(':').append(canonical(object.get(name)));
      }
      return output.append('}').toString();
    }
    if (element.isJsonPrimitive()) {
      JsonPrimitive primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber()) {
        return canonicalNumber(primitive);
      }
      if (primitive.isString()) {
        return quoted(primitive.getAsString());
      }
      return primitive.getAsBoolean() ? "true" : "false";
    }
    return JsonNull.INSTANCE.toString();
  }

  public static String requireString(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException(field + " must be a string");
    }
    String text = value.getAsString();
    if (text.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return text;
  }

  public static int requireInt(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException(field + " must be an integer");
    }
    try {
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException(field + " must be an integer", exception);
    }
  }

  public static long requireSafeLong(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException(field + " must be a safe integer");
    }
    try {
      long number = value.getAsBigDecimal().longValueExact();
      if (number < 0L || number > MAX_SAFE_INTEGER) {
        throw new IllegalArgumentException(field + " must be between 0 and " + MAX_SAFE_INTEGER);
      }
      return number;
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException(field + " must be a safe integer", exception);
    }
  }

  public static JsonObject requireObject(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonObject()) {
      throw new IllegalArgumentException(field + " must be an object");
    }
    return value.getAsJsonObject();
  }

  public static JsonArray requireArray(JsonObject object, String field) {
    JsonElement value = object.get(field);
    if (value == null || !value.isJsonArray()) {
      throw new IllegalArgumentException(field + " must be an array");
    }
    return value.getAsJsonArray();
  }

  private static String canonicalNumber(JsonPrimitive primitive) {
    double number;
    try {
      number = primitive.getAsDouble();
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException("canonical JSON number is invalid", failure);
    }
    if (!Double.isFinite(number)) {
      throw new IllegalArgumentException("canonical JSON numbers must be finite");
    }
    if (number == 0.0D) {
      return "0";
    }
    BigDecimal decimal = BigDecimal.valueOf(number).stripTrailingZeros();
    double absolute = Math.abs(number);
    if (absolute >= 0.000001D && absolute < 1.0E21D) {
      return decimal.toPlainString();
    }
    return decimal.toString().toLowerCase(java.util.Locale.ROOT);
  }

  private static String quoted(String value) {
    StringBuilder output = new StringBuilder(value.length() + 2).append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> output.append("\\\"");
        case '\\' -> output.append("\\\\");
        case '\b' -> output.append("\\b");
        case '\f' -> output.append("\\f");
        case '\n' -> output.append("\\n");
        case '\r' -> output.append("\\r");
        case '\t' -> output.append("\\t");
        default -> {
          if (character < 0x20 || Character.isSurrogate(character)
              && (Character.isHighSurrogate(character)
              ? index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))
              : index == 0 || !Character.isHighSurrogate(value.charAt(index - 1)))) {
            output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
          } else {
            output.append(character);
          }
        }
      }
    }
    return output.append('"').toString();
  }
}
