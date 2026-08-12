package art.arcane.holoui.editor.sync;

import art.arcane.holoui.board.BoardDefinition;
import com.google.gson.JsonObject;

import java.util.Map;

interface EditorSyncSnapshotSource {
  EditorSyncProject menu(String menuId, int maximumBytes);

  EditorSyncProject board(String boardId, int maximumBytes);

  EditorSyncProject fromContent(EditorSyncKind kind, String subjectId,
                                BoardDefinition board, Map<String, String> menuSources,
                                Map<String, byte[]> imageContents,
                                JsonObject immutableConstraints, int maximumBytes);
}
