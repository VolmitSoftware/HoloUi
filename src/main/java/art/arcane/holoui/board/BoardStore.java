package art.arcane.holoui.board;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

interface BoardStore {
  Path directory();

  BoardLoadResult load() throws IOException;

  Optional<BoardDefinition> get(String id);

  List<BoardDefinition> list();

  BoardDefinition create(BoardDefinition definition) throws IOException;

  BoardDefinition update(String id, long expectedRevision,
                         UnaryOperator<BoardDefinition> update) throws IOException;

  BoardDefinition rename(String id, String newId, long expectedRevision) throws IOException;

  BoardDefinition delete(String id, long expectedRevision) throws IOException;

  BoardDefinition publishExternal(BoardDefinition expected, BoardDefinition updated) throws IOException;

  BoardDefinition recoverExternal(BoardDefinition applied, BoardDefinition restored) throws IOException;
}
