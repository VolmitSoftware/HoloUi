package art.arcane.holoui.importer;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardTransform;
import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class LegacyImportApplyCoordinatorTest {
  @Test
  public void partialBoardFailureIsReportedAndLaterCandidatesContinue() {
    LegacyImportCandidate first = candidate("first");
    LegacyImportCandidate second = candidate("second");
    LegacyImportPlan plan = new LegacyImportPlan(LegacyImportSource.GHOLO, "/plugins/GHolo/holos",
        true, List.of(first, second), List.of());
    AtomicInteger boardWrites = new AtomicInteger();
    LegacyImportApplyCoordinator.ImportPublisher publisher = new LegacyImportApplyCoordinator.ImportPublisher() {
      @Override
      public CompletableFuture<Void> createMenu(LegacyImportCandidate candidate) {
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<Void> createBoard(LegacyImportCandidate candidate) {
        return boardWrites.incrementAndGet() == 1
            ? CompletableFuture.failedFuture(new IllegalStateException("disk full"))
            : CompletableFuture.completedFuture(null);
      }
    };

    LegacyImportApplyResult result = new LegacyImportApplyCoordinator().apply(plan, publisher).join();

    assertEquals(1, result.importedCount());
    assertEquals(1, result.failedCount());
    assertEquals(LegacyImportApplyEntry.Status.MENU_ONLY, result.entries().get(0).status());
    assertEquals("disk full", result.entries().get(0).message());
    assertEquals(LegacyImportApplyEntry.Status.IMPORTED, result.entries().get(1).status());
  }

  @Test
  public void synchronousPublisherFailureIsIsolatedAndLaterCandidatesContinue() {
    LegacyImportCandidate first = candidate("first");
    LegacyImportCandidate second = candidate("second");
    LegacyImportPlan plan = new LegacyImportPlan(LegacyImportSource.GHOLO, "/plugins/GHolo/holos",
        true, List.of(first, second), List.of());
    AtomicInteger menuWrites = new AtomicInteger();
    LegacyImportApplyCoordinator.ImportPublisher publisher = new LegacyImportApplyCoordinator.ImportPublisher() {
      @Override
      public CompletableFuture<Void> createMenu(LegacyImportCandidate candidate) {
        if (menuWrites.incrementAndGet() == 1) {
          throw new IllegalStateException("writer unavailable");
        }
        return CompletableFuture.completedFuture(null);
      }

      @Override
      public CompletableFuture<Void> createBoard(LegacyImportCandidate candidate) {
        return CompletableFuture.completedFuture(null);
      }
    };

    LegacyImportApplyResult result = new LegacyImportApplyCoordinator().apply(plan, publisher).join();

    assertEquals(1, result.importedCount());
    assertEquals(1, result.failedCount());
    assertEquals(LegacyImportApplyEntry.Status.FAILED, result.entries().get(0).status());
    assertEquals("writer unavailable", result.entries().get(0).message());
    assertEquals(LegacyImportApplyEntry.Status.IMPORTED, result.entries().get(1).status());
  }

  private static LegacyImportCandidate candidate(String id) {
    UUID worldUuid = UUID.fromString("00000000-0000-0000-0000-000000000802");
    BoardDefinition board = BoardDefinition.create("imports/gholo/" + id,
        "imports/gholo/" + id,
        BoardTransform.at("minecraft:overworld", worldUuid, 0, 64, 0, 0));
    return new LegacyImportCandidate(id, id + ".yml", "imports/gholo/" + id,
        "imports/gholo/" + id, "{}\n", board, LegacyImportDisposition.READY, "", List.of());
  }
}
