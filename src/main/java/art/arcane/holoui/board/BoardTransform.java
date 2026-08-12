package art.arcane.holoui.board;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record BoardTransform(String worldKey, UUID worldUuid, double x, double y, double z,
                             double yaw, double pitch, double roll, double scale) {
  public static final double MIN_SCALE = 0.05D;
  public static final double MAX_SCALE = 16.0D;

  private static final int MAX_WORLD_KEY_LENGTH = 255;
  private static final Pattern WORLD_KEY = Pattern.compile("[a-z0-9._-]+:[a-z0-9/._-]+");

  public BoardTransform {
    worldKey = canonicalWorldKey(worldKey);
    worldUuid = Objects.requireNonNull(worldUuid, "worldUuid");
    x = finite(x, "x");
    y = finite(y, "y");
    z = finite(z, "z");
    yaw = normalizeAngle(finite(yaw, "yaw"));
    pitch = normalizeAngle(finite(pitch, "pitch"));
    roll = normalizeAngle(finite(roll, "roll"));
    scale = finite(scale, "scale");
    if (scale < MIN_SCALE || scale > MAX_SCALE) {
      throw new IllegalArgumentException("scale must be between " + MIN_SCALE + " and " + MAX_SCALE);
    }
  }

  public static BoardTransform at(String worldKey, UUID worldUuid, double x, double y, double z, double yaw) {
    return new BoardTransform(worldKey, worldUuid, x, y, z, yaw, 0.0D, 0.0D, 1.0D);
  }

  private static String canonicalWorldKey(String value) {
    if (value == null) {
      throw new IllegalArgumentException("worldKey must not be null");
    }
    String normalized = value.strip().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty() || normalized.length() > MAX_WORLD_KEY_LENGTH || !WORLD_KEY.matcher(normalized).matches()) {
      throw new IllegalArgumentException("worldKey must be an explicit lowercase namespace:key");
    }
    return normalized;
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value == 0.0D ? 0.0D : value;
  }

  private static double normalizeAngle(double value) {
    double normalized = value % 360.0D;
    if (normalized >= 180.0D) {
      normalized -= 360.0D;
    } else if (normalized < -180.0D) {
      normalized += 360.0D;
    }
    return normalized == 0.0D ? 0.0D : normalized;
  }
}
