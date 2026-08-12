package art.arcane.holoui.config.menu;

import java.util.regex.Pattern;

public final class MenuIds {
  public static final int MAX_ID_LENGTH = 255;
  public static final int MAX_SEGMENT_LENGTH = 64;

  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

  private MenuIds() {
  }

  public static String require(String value) {
    if (value == null) {
      throw new IllegalArgumentException("menu id must not be null");
    }
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("menu id must not be blank");
    }
    if (normalized.length() > MAX_ID_LENGTH) {
      throw new IllegalArgumentException("menu id must be at most " + MAX_ID_LENGTH + " characters");
    }
    if (normalized.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("menu id must use forward slashes between segments");
    }
    String[] segments = normalized.split("/", -1);
    for (String segment : segments) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("menu id contains an invalid path segment: " + value);
      }
      if (segment.length() > MAX_SEGMENT_LENGTH || !SEGMENT.matcher(segment).matches()) {
        throw new IllegalArgumentException(
            "menu id segment must match [A-Za-z0-9][A-Za-z0-9._-]*: " + segment);
      }
    }
    return normalized;
  }
}
