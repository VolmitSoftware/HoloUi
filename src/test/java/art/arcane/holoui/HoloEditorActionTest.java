package art.arcane.holoui;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeParameter;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HoloEditorActionTest {
  private static final DirectorMiniMenu.Theme THEME =
      DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HOLOUI));

  @Test
  public void editIsAnInvocableLeafWithExistingMenuCompletion() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode edit = child(engine, "edit");

    assertNotNull(edit);
    assertTrue(edit.isInvocable());
    assertTrue(edit.getChildren().isEmpty());
    DirectorRuntimeParameter menu = edit.getParameters().stream()
        .filter(parameter -> !parameter.getDescriptor().isContextual())
        .findFirst()
        .orElseThrow();
    assertTrue(menu.getCustomHandlerOrNull() instanceof HoloCommand.ExistingMenuHandler);
    assertFalse(menu.getCustomHandlerOrNull().getPossibilities().contains("*"));
    assertTrue(new HoloCommand.MenuNameHandler().getPossibilities().contains("*"));
  }

  @Test
  public void playerLinkUsesThePayloadOnlyAsTheClickTarget() {
    String url = "https://example.com/#/import/menu/large-private-payload";

    String line = HoloCommand.editorEntryLine(
        url, "Open shops/main in editor", "Review before importing.", THEME);

    assertTrue(line, line.contains("<click:open_url:'" + url + "'>"));
    assertTrue(line, line.contains("Open shops/main in editor"));
    assertEquals("the payload must not also be rendered as visible text",
        line.indexOf(url), line.lastIndexOf(url));
  }

  private static DirectorRuntimeNode child(DirectorRuntimeEngine engine, String name) {
    for (DirectorRuntimeNode node : engine.getRoot().getChildren()) {
      if (node.allNames().contains(name)) {
        return node;
      }
    }
    return null;
  }
}
