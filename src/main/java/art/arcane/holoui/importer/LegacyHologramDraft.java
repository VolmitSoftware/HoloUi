package art.arcane.holoui.importer;

import java.util.List;
import java.util.Objects;

record LegacyHologramDraft(String legacyId, String sourceIdentity, LegacyLocation location,
                           double viewRange, String permission, LegacyStyle style,
                           List<LegacyRow> rows, List<String> warnings) {
  LegacyHologramDraft {
    if (legacyId == null || legacyId.isBlank()) {
      throw new IllegalArgumentException("legacy hologram id must not be blank");
    }
    if (sourceIdentity == null || sourceIdentity.isBlank()) {
      throw new IllegalArgumentException("source identity must not be blank");
    }
    location = Objects.requireNonNull(location, "location");
    if (!Double.isFinite(viewRange)) {
      throw new IllegalArgumentException("view range must be finite");
    }
    permission = permission == null || permission.isBlank() ? null : permission;
    style = Objects.requireNonNull(style, "style");
    rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("hologram has no supported text rows");
    }
  }

  record LegacyLocation(String worldReference, double x, double y, double z,
                        double yaw, double pitch) {
    LegacyLocation {
      if (worldReference == null || worldReference.isBlank()) {
        throw new IllegalArgumentException("world reference must not be blank");
      }
      requireFinite(x, "x");
      requireFinite(y, "y");
      requireFinite(z, "z");
      requireFinite(yaw, "yaw");
      requireFinite(pitch, "pitch");
    }
  }

  record LegacyRow(String text, double x, double y, double z, LegacyStyle style) {
    LegacyRow {
      if (text == null) {
        throw new IllegalArgumentException("row text must not be null");
      }
      if (text.length() > LegacyHologramScanner.MAX_ROW_TEXT_LENGTH) {
        throw new IllegalArgumentException("row text exceeds "
            + LegacyHologramScanner.MAX_ROW_TEXT_LENGTH + " characters");
      }
      requireFinite(x, "row x");
      requireFinite(y, "row y");
      requireFinite(z, "row z");
      style = Objects.requireNonNull(style, "style");
    }
  }

  record LegacyStyle(String background, Integer textOpacityPercent, Boolean textShadow,
                     String textAlignment, String billboard, Boolean seeThrough,
                     Double scaleX, Double scaleY, Double scaleZ, Integer brightness,
                     Double yaw, Double pitch, Double width, Double height) {
    static LegacyStyle empty() {
      return new LegacyStyle(null, null, null, null, null, null, null, null, null,
          null, null, null, null, null);
    }

    static LegacyStyle gholoDefaults() {
      return new LegacyStyle("#000000", 0, false, "center", "center", false,
          null, null, null, null, null, null, null, null);
    }

    LegacyStyle overlay(LegacyStyle override) {
      LegacyStyle other = Objects.requireNonNull(override, "override");
      return new LegacyStyle(
          first(other.background, background),
          first(other.textOpacityPercent, textOpacityPercent),
          first(other.textShadow, textShadow),
          first(other.textAlignment, textAlignment),
          first(other.billboard, billboard),
          first(other.seeThrough, seeThrough),
          first(other.scaleX, scaleX),
          first(other.scaleY, scaleY),
          first(other.scaleZ, scaleZ),
          first(other.brightness, brightness),
          first(other.yaw, yaw),
          first(other.pitch, pitch),
          first(other.width, width),
          first(other.height, height)
      );
    }

    private static <T> T first(T preferred, T fallback) {
      return preferred == null ? fallback : preferred;
    }
  }

  private static void requireFinite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
  }
}
