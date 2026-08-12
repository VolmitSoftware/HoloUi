package art.arcane.holoui.board;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;

public final class BoardFollowTransform {
  private BoardFollowTransform() {
  }

  public static BoardTransform resolve(BoardDefinition board, Location target) {
    return resolve(board, BoardFollowPose.from(target));
  }

  public static BoardTransform resolve(BoardDefinition board, BoardFollowPose target) {
    BoardDefinition requiredBoard = Objects.requireNonNull(board, "board");
    BoardFollowPose requiredTarget = Objects.requireNonNull(target, "target");
    BoardFollow follow = requiredBoard.follow();
    if (follow.mode() != BoardFollowMode.PLAYER) {
      return requiredBoard.transform();
    }

    BoardTransform relative = requiredBoard.transform();
    Vector offset = new Vector(relative.x(), relative.y(), relative.z());
    if (follow.rotation() == BoardFollowRotation.FULL) {
      offset.rotateAroundX(Math.toRadians(requiredTarget.pitch()));
      offset.rotateAroundY(Math.toRadians(-requiredTarget.yaw()));
    } else if (follow.rotation() == BoardFollowRotation.YAW) {
      offset.rotateAroundY(Math.toRadians(-requiredTarget.yaw()));
    }
    double yaw = follow.rotation() == BoardFollowRotation.FIXED
        ? relative.yaw()
        : requiredTarget.yaw() + relative.yaw();
    double pitch = follow.rotation() == BoardFollowRotation.FULL
        ? requiredTarget.pitch() + relative.pitch()
        : relative.pitch();
    Vector position = new Vector(requiredTarget.x(), requiredTarget.y(), requiredTarget.z()).add(offset);
    return new BoardTransform(
        requiredTarget.worldKey(),
        requiredTarget.worldUuid(),
        position.getX(),
        position.getY(),
        position.getZ(),
        yaw,
        pitch,
        relative.roll(),
        relative.scale()
    );
  }

  public static BoardTransform relativeTo(BoardTransform absolute, Location target,
                                          BoardFollowRotation rotation) {
    return relativeTo(absolute, BoardFollowPose.from(target), rotation);
  }

  public static BoardTransform relativeTo(BoardTransform absolute, BoardFollowPose target,
                                          BoardFollowRotation rotation) {
    BoardTransform requiredTransform = Objects.requireNonNull(absolute, "absolute");
    BoardFollowPose requiredTarget = Objects.requireNonNull(target, "target");
    BoardFollowRotation requiredRotation = Objects.requireNonNull(rotation, "rotation");
    if (!requiredTarget.worldUuid().equals(requiredTransform.worldUuid())) {
      throw new IllegalArgumentException("board and follow target must be in the same world");
    }

    Vector offset = new Vector(
        requiredTransform.x() - requiredTarget.x(),
        requiredTransform.y() - requiredTarget.y(),
        requiredTransform.z() - requiredTarget.z()
    );
    if (requiredRotation == BoardFollowRotation.FULL) {
      offset.rotateAroundY(Math.toRadians(requiredTarget.yaw()));
      offset.rotateAroundX(Math.toRadians(-requiredTarget.pitch()));
    } else if (requiredRotation == BoardFollowRotation.YAW) {
      offset.rotateAroundY(Math.toRadians(requiredTarget.yaw()));
    }
    double yaw = requiredRotation == BoardFollowRotation.FIXED
        ? requiredTransform.yaw()
        : requiredTransform.yaw() - requiredTarget.yaw();
    double pitch = requiredRotation == BoardFollowRotation.FULL
        ? requiredTransform.pitch() - requiredTarget.pitch()
        : requiredTransform.pitch();
    return new BoardTransform(
        requiredTarget.worldKey(),
        requiredTarget.worldUuid(),
        offset.getX(),
        offset.getY(),
        offset.getZ(),
        yaw,
        pitch,
        requiredTransform.roll(),
        requiredTransform.scale()
    );
  }
}
