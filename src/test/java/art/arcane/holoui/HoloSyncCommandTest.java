package art.arcane.holoui;

import art.arcane.holoui.editor.sync.EditorSyncKind;
import art.arcane.holoui.editor.sync.EditorSyncSessionInfo;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HoloSyncCommandTest {
  @Test
  public void exactAndUniqueTwelveCharacterPrefixesResolve() {
    EditorSyncSessionInfo first = session("abcdefghijkl-first-capability");
    EditorSyncSessionInfo second = session("zyxwvutsrqpo-second-capability");

    assertEquals(first.sessionId(), HoloSyncCommand.resolveSessionId(
        first.sessionId(), List.of(first, second)).orElseThrow());
    assertEquals(first.sessionId(), HoloSyncCommand.resolveSessionId(
        "abcdefghijkl", List.of(first, second)).orElseThrow());
    assertTrue(HoloSyncCommand.resolveSessionId("abcdefghijk", List.of(first, second)).isEmpty());
  }

  @Test
  public void ambiguousPrefixesFailClosed() {
    EditorSyncSessionInfo first = session("abcdefghijkl-first-capability");
    EditorSyncSessionInfo second = session("abcdefghijkl-second-capability");

    assertThrows(IllegalArgumentException.class, () ->
        HoloSyncCommand.resolveSessionId("abcdefghijkl", List.of(first, second)));
  }

  private static EditorSyncSessionInfo session(String id) {
    return new EditorSyncSessionInfo(id, EditorSyncKind.MENU, "fixture",
        Instant.now().plusSeconds(60L), 0L, null);
  }
}
