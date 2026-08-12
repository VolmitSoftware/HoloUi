package art.arcane.holoui.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BoardIds {
  public static final int MAX_ID_LENGTH = 255;
  public static final int MAX_SEGMENT_LENGTH = 64;
  public static final int MAX_MENU_ID_LENGTH = 255;

  private static final Pattern SEGMENT = Pattern.compile("[a-z0-9][a-z0-9._-]*");
  private static final Pattern MENU_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

  private BoardIds() {
  }

  public static String canonicalize(String value) {
    if (value == null) {
      throw new IllegalArgumentException("board id must not be null");
    }

    String normalized = value.strip().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("board id must not be blank");
    }
    if (normalized.length() > MAX_ID_LENGTH) {
      throw new IllegalArgumentException("board id must be at most " + MAX_ID_LENGTH + " characters");
    }
    if (normalized.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("board id must use forward slashes between segments");
    }

    String[] rawSegments = normalized.split("/", -1);
    List<String> segments = new ArrayList<>(rawSegments.length);
    for (String segment : rawSegments) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("board id contains an invalid path segment: " + value);
      }
      if (segment.length() > MAX_SEGMENT_LENGTH || !SEGMENT.matcher(segment).matches()) {
        throw new IllegalArgumentException("board id segment must match [a-z0-9][a-z0-9._-]*: " + segment);
      }
      segments.add(segment);
    }
    return String.join("/", segments);
  }

  public static String requireMenuReference(String value) {
    if (value == null) {
      throw new IllegalArgumentException("root menu id must not be null");
    }

    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("root menu id must not be blank");
    }
    if (normalized.length() > MAX_MENU_ID_LENGTH) {
      throw new IllegalArgumentException("root menu id must be at most " + MAX_MENU_ID_LENGTH + " characters");
    }

    String[] segments = normalized.split("/", -1);
    for (String segment : segments) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("root menu id contains an invalid path segment: " + value);
      }
      if (segment.length() > MAX_SEGMENT_LENGTH || !MENU_SEGMENT.matcher(segment).matches()) {
        throw new IllegalArgumentException("root menu id segment must match [A-Za-z0-9][A-Za-z0-9._-]*: " + segment);
      }
    }
    return normalized;
  }
}
