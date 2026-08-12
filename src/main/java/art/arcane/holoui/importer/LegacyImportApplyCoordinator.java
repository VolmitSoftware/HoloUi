package art.arcane.holoui.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class LegacyImportApplyCoordinator {
  CompletableFuture<LegacyImportApplyResult> apply(LegacyImportPlan plan,
                                                   ImportPublisher publisher) {
    LegacyImportPlan requiredPlan = Objects.requireNonNull(plan, "plan");
    ImportPublisher requiredPublisher = Objects.requireNonNull(publisher, "publisher");
    List<LegacyImportApplyEntry> entries = new ArrayList<>(requiredPlan.candidates().size());
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    for (LegacyImportCandidate candidate : requiredPlan.candidates()) {
      chain = chain.thenCompose(ignored -> applyOne(candidate, requiredPublisher, entries));
    }
    return chain.thenApply(ignored -> new LegacyImportApplyResult(requiredPlan, entries));
  }

  private CompletableFuture<Void> applyOne(LegacyImportCandidate candidate,
                                           ImportPublisher publisher,
                                           List<LegacyImportApplyEntry> entries) {
    if (candidate.disposition() == LegacyImportDisposition.CONFLICT) {
      entries.add(entry(candidate, LegacyImportApplyEntry.Status.SKIPPED,
          candidate.dispositionReason()));
      return CompletableFuture.completedFuture(null);
    }
    if (candidate.disposition() == LegacyImportDisposition.RESUME_BOARD) {
      return createBoard(publisher, candidate).handle((ignored, failure) -> {
        if (failure == null) {
          entries.add(entry(candidate, LegacyImportApplyEntry.Status.RESUMED, ""));
        } else {
          entries.add(entry(candidate, LegacyImportApplyEntry.Status.FAILED, safeMessage(failure)));
        }
        return null;
      });
    }

    return createMenu(publisher, candidate).handle((ignored, menuFailure) -> menuFailure)
        .thenCompose(menuFailure -> {
          if (menuFailure != null) {
            entries.add(entry(candidate, LegacyImportApplyEntry.Status.FAILED,
                safeMessage(menuFailure)));
            return CompletableFuture.completedFuture(null);
          }
          return createBoard(publisher, candidate).handle((ignored, boardFailure) -> {
            if (boardFailure == null) {
              entries.add(entry(candidate, LegacyImportApplyEntry.Status.IMPORTED, ""));
            } else {
              entries.add(entry(candidate, LegacyImportApplyEntry.Status.MENU_ONLY,
                  safeMessage(boardFailure)));
            }
            return null;
          });
        });
  }

  private static CompletableFuture<Void> createMenu(ImportPublisher publisher,
                                                     LegacyImportCandidate candidate) {
    try {
      return Objects.requireNonNull(publisher.createMenu(candidate),
          "menu publisher returned a null future");
    } catch (RuntimeException failure) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  private static CompletableFuture<Void> createBoard(ImportPublisher publisher,
                                                      LegacyImportCandidate candidate) {
    try {
      return Objects.requireNonNull(publisher.createBoard(candidate),
          "board publisher returned a null future");
    } catch (RuntimeException failure) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  private static LegacyImportApplyEntry entry(LegacyImportCandidate candidate,
                                              LegacyImportApplyEntry.Status status,
                                              String message) {
    return new LegacyImportApplyEntry(candidate.legacyId(), status, message);
  }

  private static String safeMessage(Throwable failure) {
    Throwable root = failure;
    while ((root instanceof CompletionException || root instanceof ExecutionException)
        && root.getCause() != null) {
      root = root.getCause();
    }
    String message = root.getMessage();
    return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
  }

  interface ImportPublisher {
    CompletableFuture<Void> createMenu(LegacyImportCandidate candidate);

    CompletableFuture<Void> createBoard(LegacyImportCandidate candidate);
  }
}
