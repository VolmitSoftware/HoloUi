package art.arcane.holoui;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardFollow;
import art.arcane.holoui.board.BoardFollowRotation;
import art.arcane.holoui.board.BoardFollowTransform;
import art.arcane.holoui.board.BoardTransform;
import art.arcane.holoui.board.BoardVisibility;
import art.arcane.holoui.board.BoardVisibilityMode;
import org.bukkit.Location;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

final class BoardCommandSupport {
  private BoardCommandSupport() {
  }

  static double coordinate(String input, double base, String field) {
    if (input == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
    String token = input.strip();
    if (token.isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }

    double value;
    if (token.charAt(0) == '~') {
      String relative = token.substring(1);
      value = base + (relative.isEmpty() ? 0.0D : number(relative, field));
    } else {
      value = number(token, field);
    }
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must resolve to a finite number");
    }
    return value == 0.0D ? 0.0D : value;
  }

  static BoardTransform move(BoardTransform current, String x, String y, String z) {
    BoardTransform transform = Objects.requireNonNull(current, "current");
    return new BoardTransform(
        transform.worldKey(),
        transform.worldUuid(),
        coordinate(x, transform.x(), "x"),
        coordinate(y, transform.y(), "y"),
        coordinate(z, transform.z(), "z"),
        transform.yaw(),
        transform.pitch(),
        transform.roll(),
        transform.scale()
    );
  }

  static BoardTransform moveTo(BoardTransform current, String worldKey, UUID worldUuid,
                               double x, double y, double z) {
    BoardTransform transform = Objects.requireNonNull(current, "current");
    return new BoardTransform(
        worldKey,
        worldUuid,
        x,
        y,
        z,
        transform.yaw(),
        transform.pitch(),
        transform.roll(),
        transform.scale()
    );
  }

  static BoardTransform rotate(BoardTransform current, String yaw, String pitch, String roll) {
    BoardTransform transform = Objects.requireNonNull(current, "current");
    return new BoardTransform(
        transform.worldKey(),
        transform.worldUuid(),
        transform.x(),
        transform.y(),
        transform.z(),
        coordinate(yaw, transform.yaw(), "yaw"),
        coordinate(pitch, transform.pitch(), "pitch"),
        coordinate(roll, transform.roll(), "roll"),
        transform.scale()
    );
  }

  static BoardTransform scale(BoardTransform current, String scale) {
    BoardTransform transform = Objects.requireNonNull(current, "current");
    return new BoardTransform(
        transform.worldKey(),
        transform.worldUuid(),
        transform.x(),
        transform.y(),
        transform.z(),
        transform.yaw(),
        transform.pitch(),
        transform.roll(),
        coordinate(scale, transform.scale(), "scale")
    );
  }

  static BoardDefinition reencodeEffectiveTransform(BoardDefinition current,
                                                    BoardTransform effectiveTransform,
                                                    Location followTarget) {
    BoardDefinition board = Objects.requireNonNull(current, "current");
    BoardTransform effective = Objects.requireNonNull(effectiveTransform, "effectiveTransform");
    if (board.follow().targetPlayerUuid() == null) {
      return board.withTransform(effective);
    }
    BoardTransform relative = BoardFollowTransform.relativeTo(
        effective,
        Objects.requireNonNull(followTarget, "followTarget"),
        board.follow().rotation()
    );
    return board.withTransform(relative);
  }

  static BoardDefinition follow(BoardDefinition current, BoardTransform effectiveTransform,
                                Location target, UUID targetPlayerUuid,
                                BoardFollowRotation rotation) {
    BoardDefinition board = Objects.requireNonNull(current, "current");
    BoardTransform relative = BoardFollowTransform.relativeTo(
        Objects.requireNonNull(effectiveTransform, "effectiveTransform"),
        Objects.requireNonNull(target, "target"),
        Objects.requireNonNull(rotation, "rotation")
    );
    return board.withTransform(relative)
        .withFollow(BoardFollow.player(Objects.requireNonNull(targetPlayerUuid, "targetPlayerUuid"), rotation));
  }

  static BoardDefinition unfollow(BoardDefinition current, BoardTransform effectiveTransform) {
    return Objects.requireNonNull(current, "current")
        .withTransform(Objects.requireNonNull(effectiveTransform, "effectiveTransform"))
        .withFollow(BoardFollow.none());
  }

  static BoardTransform align(BoardTransform current, BoardTransform reference, String axes) {
    BoardTransform transform = Objects.requireNonNull(current, "current");
    BoardTransform source = Objects.requireNonNull(reference, "reference");
    if (!transform.worldUuid().equals(source.worldUuid()) || !transform.worldKey().equals(source.worldKey())) {
      throw new IllegalArgumentException("boards must be in the same world to align");
    }

    String selected = axes(axes);
    return new BoardTransform(
        transform.worldKey(),
        transform.worldUuid(),
        selected.indexOf('x') >= 0 ? source.x() : transform.x(),
        selected.indexOf('y') >= 0 ? source.y() : transform.y(),
        selected.indexOf('z') >= 0 ? source.z() : transform.z(),
        transform.yaw(),
        transform.pitch(),
        transform.roll(),
        transform.scale()
    );
  }

  static BoardVisibility visibility(BoardVisibility current, BoardVisibilityMode mode,
                                    String viewPermission, String interactPermission) {
    BoardVisibility existing = Objects.requireNonNull(current, "current");
    BoardVisibilityMode requestedMode = Objects.requireNonNull(mode, "mode");
    String view = permission(viewPermission);
    String interact = permission(interactPermission);
    return new BoardVisibility(requestedMode, view, interact,
        existing.viewRange(), existing.interactionRange());
  }

  static BoardVisibility permissions(BoardVisibility current, String viewPermission,
                                     String interactPermission) {
    BoardVisibility existing = Objects.requireNonNull(current, "current");
    String view = permission(viewPermission);
    String interact = permission(interactPermission);
    BoardVisibilityMode mode;
    if (view != null) {
      mode = BoardVisibilityMode.PERMISSION;
    } else if (existing.mode() == BoardVisibilityMode.HIDDEN && interact == null) {
      mode = BoardVisibilityMode.HIDDEN;
    } else {
      mode = BoardVisibilityMode.PUBLIC;
    }
    return new BoardVisibility(mode, view, interact,
        existing.viewRange(), existing.interactionRange());
  }

  static BoardDefinition copy(BoardDefinition source, String newId) {
    BoardDefinition board = Objects.requireNonNull(source, "source");
    return new BoardDefinition(
        BoardDefinition.CURRENT_SCHEMA_VERSION,
        newId,
        UUID.randomUUID(),
        BoardDefinition.INITIAL_REVISION,
        board.rootMenuId(),
        board.transform(),
        board.follow(),
        board.visibility()
    );
  }

  static double nonNegativeFinite(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0D) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }

  static Throwable rootCause(Throwable failure) {
    Throwable cause = failure;
    while (cause != null && cause.getCause() != null
        && (cause instanceof java.util.concurrent.CompletionException
        || cause instanceof java.util.concurrent.ExecutionException)) {
      cause = cause.getCause();
    }
    return cause == null ? failure : cause;
  }

  private static double number(String input, String field) {
    try {
      return Double.parseDouble(input);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(field + " must be a number or ~relative value");
    }
  }

  static String axes(String input) {
    if (input == null) {
      throw new IllegalArgumentException("axes must not be null");
    }
    String axes = input.strip().toLowerCase(Locale.ROOT);
    return switch (axes) {
      case "x", "y", "z", "xy", "xz", "yz", "xyz" -> axes;
      default -> throw new IllegalArgumentException("axes must be x, y, z, xy, xz, yz, or xyz");
    };
  }

  private static String permission(String input) {
    if (input == null) {
      return null;
    }
    String value = input.strip();
    return value.isEmpty() || value.equals("-") ? null : value;
  }
}
