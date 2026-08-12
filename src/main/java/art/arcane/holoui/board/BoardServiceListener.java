package art.arcane.holoui.board;

import java.util.List;

public interface BoardServiceListener {
  default void boardCreated(BoardDefinition board) {
  }

  default void boardUpdated(BoardDefinition previous, BoardDefinition updated) {
  }

  default void boardDeleted(BoardDefinition board) {
  }

  default void boardsReloaded(BoardLoadResult result, List<BoardDefinition> boards) {
  }
}
