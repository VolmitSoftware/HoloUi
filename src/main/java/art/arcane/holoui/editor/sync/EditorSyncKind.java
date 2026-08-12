package art.arcane.holoui.editor.sync;

import java.util.Locale;

public enum EditorSyncKind {
  MENU,
  BOARD;

  public String wireName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static EditorSyncKind parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("kind must not be null");
    }
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
