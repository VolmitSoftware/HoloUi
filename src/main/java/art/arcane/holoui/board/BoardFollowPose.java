package art.arcane.holoui.board;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public record BoardFollowPose(String worldKey, UUID worldUuid, double x, double y, double z,
                              float yaw, float pitch) {
  public BoardFollowPose {
    worldKey = Objects.requireNonNull(worldKey, "worldKey");
    worldUuid = Objects.requireNonNull(worldUuid, "worldUuid");
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
        || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
      throw new IllegalArgumentException("follow pose must be finite");
    }
  }

  public static BoardFollowPose from(Location location) {
    Location requiredLocation = Objects.requireNonNull(location, "location");
    World world = Objects.requireNonNull(requiredLocation.getWorld(), "location world");
    return new BoardFollowPose(
        world.getKey().toString(),
        world.getUID(),
        requiredLocation.getX(),
        requiredLocation.getY(),
        requiredLocation.getZ(),
        requiredLocation.getYaw(),
        requiredLocation.getPitch()
    );
  }
}
