package art.arcane.holoui.board;

import java.util.Objects;
import java.util.UUID;

public record BoardDefinition(int schemaVersion, String id, UUID uuid, long revision, String rootMenuId,
                              BoardTransform transform, BoardFollow follow, BoardVisibility visibility) {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  public static final long INITIAL_REVISION = 1L;
  public static final long MAX_SAFE_REVISION = 9_007_199_254_740_991L;

  public BoardDefinition {
    if (schemaVersion != CURRENT_SCHEMA_VERSION) {
      throw new IllegalArgumentException("unsupported board schemaVersion: " + schemaVersion);
    }
    id = BoardIds.canonicalize(id);
    uuid = Objects.requireNonNull(uuid, "uuid");
    if (revision < INITIAL_REVISION || revision > MAX_SAFE_REVISION) {
      throw new IllegalArgumentException("revision must be between " + INITIAL_REVISION
          + " and " + MAX_SAFE_REVISION);
    }
    rootMenuId = BoardIds.requireMenuReference(rootMenuId);
    transform = Objects.requireNonNull(transform, "transform");
    follow = Objects.requireNonNull(follow, "follow");
    visibility = Objects.requireNonNull(visibility, "visibility");
  }

  public static BoardDefinition create(String id, String rootMenuId, BoardTransform transform) {
    return new BoardDefinition(CURRENT_SCHEMA_VERSION, id, UUID.randomUUID(), INITIAL_REVISION, rootMenuId,
        transform, BoardFollow.none(), BoardVisibility.publicAccess());
  }

  public BoardDefinition withRootMenu(String rootMenuId) {
    return new BoardDefinition(schemaVersion, id, uuid, revision, rootMenuId, transform, follow, visibility);
  }

  public BoardDefinition withTransform(BoardTransform transform) {
    return new BoardDefinition(schemaVersion, id, uuid, revision, rootMenuId, transform, follow, visibility);
  }

  public BoardDefinition withFollow(BoardFollow follow) {
    return new BoardDefinition(schemaVersion, id, uuid, revision, rootMenuId, transform, follow, visibility);
  }

  public BoardDefinition withVisibility(BoardVisibility visibility) {
    return new BoardDefinition(schemaVersion, id, uuid, revision, rootMenuId, transform, follow, visibility);
  }

  BoardDefinition withRevision(long revision) {
    return new BoardDefinition(schemaVersion, id, uuid, revision, rootMenuId, transform, follow, visibility);
  }
}
