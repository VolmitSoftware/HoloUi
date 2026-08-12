package art.arcane.holoui.board;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class BoardDomainTest {
  private static final UUID WORLD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000201");

  @Test
  public void nestedIdsAreCanonicalAndPortable() {
    assertEquals("spawn/shops/main-1", BoardIds.canonicalize("  Spawn/Shops/Main-1  "));
    assertEquals("Menus/Shop.Main", BoardIds.requireMenuReference(" Menus/Shop.Main "));
    assertEquals(BoardIds.MAX_ID_LENGTH,
        BoardIds.canonicalize("a".repeat(63) + "/" + "b".repeat(64) + "/" + "c".repeat(64) + "/" + "d".repeat(61)).length());
  }

  @Test
  public void idsRejectTraversalAndAmbiguousPaths() {
    String[] invalid = {"../outside", "a/../../outside", "/absolute", "a//b", "a/./b", "a\\b", ".hidden", "a/%2e%2e/b"};
    for (String id : invalid) {
      assertThrows(id, IllegalArgumentException.class, () -> BoardIds.canonicalize(id));
    }
    assertThrows(IllegalArgumentException.class, () -> BoardIds.canonicalize("a".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> BoardIds.canonicalize(" "));
    assertThrows(IllegalArgumentException.class, () -> BoardIds.canonicalize(null));
    assertThrows(IllegalArgumentException.class, () -> BoardIds.requireMenuReference("menus/../shop"));
    assertThrows(IllegalArgumentException.class, () -> BoardIds.requireMenuReference("menus//shop"));
  }

  @Test
  public void transformsRequireCompleteFiniteWorldDataAndNormalizeAngles() {
    BoardTransform transform = new BoardTransform("Example:Lobby/Main", WORLD_UUID, -0.0D, 64.5D, 3.0D,
        540.0D, -540.0D, 360.0D, 1.25D);

    assertEquals("example:lobby/main", transform.worldKey());
    assertEquals(WORLD_UUID, transform.worldUuid());
    assertEquals(0.0D, transform.x(), 0.0D);
    assertEquals(-180.0D, transform.yaw(), 0.0D);
    assertEquals(-180.0D, transform.pitch(), 0.0D);
    assertEquals(0.0D, transform.roll(), 0.0D);

    assertThrows(NullPointerException.class,
        () -> BoardTransform.at("example:lobby", null, 0.0D, 0.0D, 0.0D, 0.0D));
    assertThrows(IllegalArgumentException.class,
        () -> BoardTransform.at(null, WORLD_UUID, 0.0D, 0.0D, 0.0D, 0.0D));
    assertThrows(IllegalArgumentException.class,
        () -> BoardTransform.at("lobby", WORLD_UUID, 0.0D, 0.0D, 0.0D, 0.0D));
    assertThrows(IllegalArgumentException.class,
        () -> new BoardTransform("example:lobby", WORLD_UUID, Double.NaN, 0.0D, 0.0D,
            0.0D, 0.0D, 0.0D, 1.0D));
    assertThrows(IllegalArgumentException.class,
        () -> new BoardTransform("example:lobby", WORLD_UUID, 0.0D, 0.0D, 0.0D,
            0.0D, 0.0D, 0.0D, BoardTransform.MIN_SCALE - 0.01D));
  }

  @Test
  public void followAndVisibilityRecordsEnforceCoherentStates() {
    assertEquals(BoardFollow.none(), new BoardFollow(BoardFollowMode.NONE, null, BoardFollowRotation.FIXED));
    assertEquals(PLAYER_UUID, BoardFollow.player(PLAYER_UUID, BoardFollowRotation.YAW).targetPlayerUuid());
    assertThrows(IllegalArgumentException.class,
        () -> new BoardFollow(BoardFollowMode.PLAYER, null, BoardFollowRotation.YAW));
    assertThrows(IllegalArgumentException.class,
        () -> new BoardFollow(BoardFollowMode.NONE, PLAYER_UUID, BoardFollowRotation.FIXED));

    BoardVisibility permission = BoardVisibility.permission(" HoloUi.Board.View.Spawn ", "holoui.board.use.spawn");
    assertEquals("holoui.board.view.spawn", permission.viewPermission());
    assertEquals("holoui.board.use.spawn", permission.interactPermission());
    assertEquals(BoardVisibility.DEFAULT_VIEW_RANGE, permission.viewRange(), 0.0D);
    assertEquals(BoardVisibility.DEFAULT_INTERACTION_RANGE, permission.interactionRange(), 0.0D);
    assertEquals(24.0D, permission.withRanges(24.0D, 4.5D).viewRange(), 0.0D);
    BoardVisibility maximum = permission.withRanges(
        BoardVisibility.MAX_VIEW_RANGE,
        BoardVisibility.MAX_INTERACTION_RANGE
    );
    assertEquals(BoardVisibility.MAX_VIEW_RANGE, maximum.viewRange(), 0.0D);
    assertEquals(BoardVisibility.MAX_INTERACTION_RANGE, maximum.interactionRange(), 0.0D);
    assertThrows(IllegalArgumentException.class,
        () -> new BoardVisibility(BoardVisibilityMode.PERMISSION, null, null, 24.0D, 4.5D));
    assertThrows(IllegalArgumentException.class,
        () -> new BoardVisibility(BoardVisibilityMode.HIDDEN, null, "holoui.board.use", 24.0D, 4.5D));
    assertThrows(IllegalArgumentException.class, () -> permission.withRanges(Double.NaN, 4.5D));
    assertThrows(IllegalArgumentException.class, () -> permission.withRanges(24.0D, 0.0D));
    assertThrows(IllegalArgumentException.class, () -> permission.withRanges(4.0D, 4.5D));
    assertThrows(IllegalArgumentException.class,
        () -> permission.withRanges(BoardVisibility.MAX_VIEW_RANGE + 1.0D, 4.5D));
    assertThrows(IllegalArgumentException.class,
        () -> permission.withRanges(64.0D, BoardVisibility.MAX_INTERACTION_RANGE + 1.0D));
  }

  @Test
  public void definitionsStartVersionedWithIndependentStableIdentities() {
    BoardTransform transform = BoardTransform.at("example:missing_world", WORLD_UUID, 1.0D, 2.0D, 3.0D, 45.0D);
    BoardDefinition first = BoardDefinition.create("Boards/Main", "Shop", transform);
    BoardDefinition second = BoardDefinition.create("Boards/Other", "Shop", transform);

    assertEquals(BoardDefinition.CURRENT_SCHEMA_VERSION, first.schemaVersion());
    assertEquals(BoardDefinition.INITIAL_REVISION, first.revision());
    assertEquals("boards/main", first.id());
    assertEquals("Shop", first.rootMenuId());
    assertNotEquals(first.uuid(), second.uuid());
    assertEquals(first.uuid(), first.withVisibility(BoardVisibility.hidden()).uuid());
    assertEquals(first.revision(), first.withVisibility(BoardVisibility.hidden()).revision());

    assertThrows(IllegalArgumentException.class,
        () -> new BoardDefinition(0, "boards/main", first.uuid(), 1L, "Shop", transform,
            BoardFollow.none(), BoardVisibility.publicAccess()));
    assertThrows(IllegalArgumentException.class,
        () -> new BoardDefinition(1, "boards/main", first.uuid(), 0L, "Shop", transform,
            BoardFollow.none(), BoardVisibility.publicAccess()));
  }
}
