package art.arcane.holoui.board;

import java.util.Objects;
import java.util.UUID;

public record BoardFollow(BoardFollowMode mode, UUID targetPlayerUuid, BoardFollowRotation rotation) {
  public BoardFollow {
    mode = Objects.requireNonNull(mode, "mode");
    rotation = Objects.requireNonNull(rotation, "rotation");

    if (mode == BoardFollowMode.NONE) {
      if (targetPlayerUuid != null || rotation != BoardFollowRotation.FIXED) {
        throw new IllegalArgumentException("non-following boards cannot declare a target or rotation mode");
      }
    } else if (targetPlayerUuid == null) {
      throw new IllegalArgumentException("player-following boards require targetPlayerUuid");
    }
  }

  public static BoardFollow none() {
    return new BoardFollow(BoardFollowMode.NONE, null, BoardFollowRotation.FIXED);
  }

  public static BoardFollow player(UUID targetPlayerUuid, BoardFollowRotation rotation) {
    return new BoardFollow(BoardFollowMode.PLAYER, targetPlayerUuid, rotation);
  }
}
