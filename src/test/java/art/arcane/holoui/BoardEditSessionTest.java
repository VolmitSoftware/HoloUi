package art.arcane.holoui;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardTransform;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class BoardEditSessionTest {
  private static final UUID WORLD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000411");

  @Test
  public void stagesDefinitionAndEffectiveTransformAsOneSnapshot() {
    BoardDefinition base = board();
    BoardEditSession session = new BoardEditSession(base, base.transform());
    BoardEditSession.Snapshot expected = session.snapshot();
    BoardTransform effective = new BoardTransform(
        "example:world", WORLD_UUID, 9D, 8D, 7D, 6D, 5D, 4D, 2D);
    BoardDefinition changed = base.withRootMenu("secondary").withTransform(effective);

    BoardEditSession.Snapshot staged = session.stage(expected, changed, effective);

    assertEquals(changed, staged.definition());
    assertEquals(effective, staged.effectiveTransform());
    assertEquals(staged, session.snapshot());
  }

  @Test
  public void stalePreparedMutationCannotOverwriteNewerStagedState() {
    BoardDefinition base = board();
    BoardEditSession session = new BoardEditSession(base, base.transform());
    BoardEditSession.Snapshot stale = session.snapshot();
    BoardDefinition first = base.withRootMenu("first");
    session.stage(stale, first, base.transform());

    assertThrows(IllegalStateException.class,
        () -> session.stage(stale, base.withRootMenu("second"), base.transform()));
  }

  @Test
  public void saveLocksFurtherStagesUntilAFailedSaveIsRetried() {
    BoardDefinition base = board();
    BoardEditSession session = new BoardEditSession(base, base.transform());

    assertEquals(base, session.beginSave());
    assertNull(session.beginSave());
    assertNull(session.stage(session.snapshot(), base.withRootMenu("blocked"), base.transform()));

    session.retrySave();
    BoardEditSession.Snapshot staged = session.stage(
        session.snapshot(), base.withRootMenu("allowed"), base.transform());
    assertEquals("allowed", staged.definition().rootMenuId());
  }

  @Test
  public void stagedEditsCannotChangeIdentityOrRevision() {
    BoardDefinition base = board();
    BoardEditSession session = new BoardEditSession(base, base.transform());
    BoardDefinition changedIdentity = new BoardDefinition(
        base.schemaVersion(), "other", base.uuid(), base.revision(), base.rootMenuId(),
        base.transform(), base.follow(), base.visibility());

    assertThrows(IllegalArgumentException.class,
        () -> session.stage(session.snapshot(), changedIdentity, base.transform()));
  }

  private static BoardDefinition board() {
    return BoardDefinition.create("board", "main", new BoardTransform(
        "example:world", WORLD_UUID, 1D, 2D, 3D, 4D, 5D, 6D, 1D));
  }
}
