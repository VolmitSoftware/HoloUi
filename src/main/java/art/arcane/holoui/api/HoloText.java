package art.arcane.holoui.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HoloText {
  static final int MAX_ID_LENGTH = 64;
  static final int MAX_MARKUP_LENGTH = 4096;
  static final int MAX_PATH_LENGTH = 256;

  private static final int DELETE = 0x7F;

  private HoloText() {
  }

  static String sanitizeId(String value) {
    if (value == null) {
      throw new IllegalArgumentException("id must not be null");
    }

    StringBuilder builder = new StringBuilder(Math.min(value.length(), MAX_ID_LENGTH));
    for (int index = 0; index < value.length() && builder.length() < MAX_ID_LENGTH; index++) {
      char character = value.charAt(index);
      boolean allowed = (character >= 'a' && character <= 'z')
          || (character >= 'A' && character <= 'Z')
          || (character >= '0' && character <= '9')
          || character == '_' || character == '-' || character == '.';
      if (allowed) {
        builder.append(character);
      }
    }

    if (builder.isEmpty()) {
      throw new IllegalArgumentException("id must contain at least one of A-Z a-z 0-9 _ - .");
    }

    return builder.toString();
  }

  static String sanitizeMarkup(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }

    String trimmed = value.length() > MAX_MARKUP_LENGTH ? value.substring(0, MAX_MARKUP_LENGTH) : value;
    StringBuilder builder = new StringBuilder(trimmed.length());
    for (int index = 0; index < trimmed.length(); index++) {
      char character = trimmed.charAt(index);
      if (character == '\n') {
        builder.append(character);
        continue;
      }
      builder.append(character < ' ' || character == DELETE ? ' ' : character);
    }

    return builder.toString();
  }

  static String sanitizePath(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("image path must not be blank");
    }

    if (value.length() > MAX_PATH_LENGTH) {
      throw new IllegalArgumentException("image path must be at most " + MAX_PATH_LENGTH + " characters");
    }

    String normalized = value.replace('\\', '/').strip();
    for (int index = 0; index < normalized.length(); index++) {
      char character = normalized.charAt(index);
      if (character < ' ' || character == DELETE) {
        throw new IllegalArgumentException("image path must not contain control characters");
      }
    }

    if (normalized.isEmpty() || normalized.startsWith("/") || normalized.indexOf(':') >= 0) {
      throw new IllegalArgumentException("image path must be relative to the HoloUi images folder: " + value);
    }

    for (String segment : normalized.split("/")) {
      if (segment.equals("..")) {
        throw new IllegalArgumentException("image path must not escape the HoloUi images folder: " + value);
      }
    }

    return normalized;
  }

  static List<String> sanitizePaths(List<String> values) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("an animated image needs at least one frame path");
    }

    List<String> sanitized = new ArrayList<>(values.size());
    for (String value : values) {
      sanitized.add(sanitizePath(value));
    }

    return List.copyOf(sanitized);
  }

  static void requireDistinctIds(List<HoloComponent> components) {
    Set<String> seen = new HashSet<>(components.size());
    for (HoloComponent component : components) {
      if (component == null) {
        throw new IllegalArgumentException("components must not contain null");
      }
      if (!seen.add(component.id())) {
        throw new IllegalArgumentException("duplicate component id: " + component.id());
      }
    }
  }
}
