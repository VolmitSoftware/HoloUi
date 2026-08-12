package art.arcane.holoui.board;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record BoardVisibility(BoardVisibilityMode mode, String viewPermission, String interactPermission,
                              double viewRange, double interactionRange) {
  public static final double DEFAULT_VIEW_RANGE = 64.0D;
  public static final double DEFAULT_INTERACTION_RANGE = 8.0D;
  public static final double MAX_VIEW_RANGE = 256.0D;
  public static final double MAX_INTERACTION_RANGE = 32.0D;

  private static final Pattern PERMISSION = Pattern.compile("[a-z0-9][a-z0-9._-]*");

  public BoardVisibility {
    mode = Objects.requireNonNull(mode, "mode");
    viewPermission = normalizePermission(viewPermission, "viewPermission");
    interactPermission = normalizePermission(interactPermission, "interactPermission");
    viewRange = requirePositiveFinite(viewRange, "viewRange");
    interactionRange = requirePositiveFinite(interactionRange, "interactionRange");

    if (mode == BoardVisibilityMode.PERMISSION && viewPermission == null) {
      throw new IllegalArgumentException("permission visibility requires viewPermission");
    }
    if (mode != BoardVisibilityMode.PERMISSION && viewPermission != null) {
      throw new IllegalArgumentException("viewPermission is only valid for permission visibility");
    }
    if (mode == BoardVisibilityMode.HIDDEN && interactPermission != null) {
      throw new IllegalArgumentException("hidden boards cannot declare interactPermission");
    }
    if (interactionRange > viewRange) {
      throw new IllegalArgumentException("interactionRange must not exceed viewRange");
    }
    if (viewRange > MAX_VIEW_RANGE) {
      throw new IllegalArgumentException("viewRange must not exceed " + MAX_VIEW_RANGE);
    }
    if (interactionRange > MAX_INTERACTION_RANGE) {
      throw new IllegalArgumentException("interactionRange must not exceed " + MAX_INTERACTION_RANGE);
    }
  }

  public static BoardVisibility publicAccess() {
    return new BoardVisibility(BoardVisibilityMode.PUBLIC, null, null,
        DEFAULT_VIEW_RANGE, DEFAULT_INTERACTION_RANGE);
  }

  public static BoardVisibility publicView(String interactPermission) {
    return new BoardVisibility(BoardVisibilityMode.PUBLIC, null, interactPermission,
        DEFAULT_VIEW_RANGE, DEFAULT_INTERACTION_RANGE);
  }

  public static BoardVisibility permission(String viewPermission, String interactPermission) {
    return new BoardVisibility(BoardVisibilityMode.PERMISSION, viewPermission, interactPermission,
        DEFAULT_VIEW_RANGE, DEFAULT_INTERACTION_RANGE);
  }

  public static BoardVisibility hidden() {
    return new BoardVisibility(BoardVisibilityMode.HIDDEN, null, null,
        DEFAULT_VIEW_RANGE, DEFAULT_INTERACTION_RANGE);
  }

  public BoardVisibility withRanges(double viewRange, double interactionRange) {
    return new BoardVisibility(mode, viewPermission, interactPermission, viewRange, interactionRange);
  }

  private static String normalizePermission(String value, String field) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty() || !PERMISSION.matcher(normalized).matches()) {
      throw new IllegalArgumentException(field + " must be a Bukkit permission node");
    }
    return normalized;
  }

  private static double requirePositiveFinite(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0D) {
      throw new IllegalArgumentException(field + " must be finite and greater than zero");
    }
    return value;
  }
}
