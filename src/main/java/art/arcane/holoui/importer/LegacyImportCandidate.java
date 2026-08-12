package art.arcane.holoui.importer;

import art.arcane.holoui.board.BoardDefinition;

import java.util.List;
import java.util.Objects;

public record LegacyImportCandidate(String legacyId, String sourceIdentity, String menuId,
                                    String boardId, String menuSource, BoardDefinition board,
                                    LegacyImportDisposition disposition, String dispositionReason,
                                    List<String> warnings) {
  public LegacyImportCandidate {
    legacyId = requireText(legacyId, "legacyId");
    sourceIdentity = requireText(sourceIdentity, "sourceIdentity");
    menuId = requireText(menuId, "menuId");
    boardId = requireText(boardId, "boardId");
    menuSource = requireText(menuSource, "menuSource");
    board = Objects.requireNonNull(board, "board");
    disposition = Objects.requireNonNull(disposition, "disposition");
    dispositionReason = dispositionReason == null ? "" : dispositionReason;
    warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
  }

  public LegacyImportCandidate withDisposition(LegacyImportDisposition nextDisposition, String reason) {
    return new LegacyImportCandidate(legacyId, sourceIdentity, menuId, boardId, menuSource, board,
        nextDisposition, reason, warnings);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
