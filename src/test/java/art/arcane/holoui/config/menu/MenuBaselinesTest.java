package art.arcane.holoui.config.menu;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ButtonComponentData;
import art.arcane.holoui.config.components.DecoComponentData;
import art.arcane.holoui.config.icon.TextIconData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MenuBaselinesTest {
  @Test
  public void blankHologramParsesAsAUsableMenu() {
    MenuDocument document = MenuDocumentParser.parse("sign", MenuBaselines.blankHologramSource());

    assertEquals("sign", document.id());
    assertFalse(document.definition().isFollowPlayer());
    assertEquals(3, document.definition().getComponents().size());
    assertEquals("title", document.definition().getComponents().get(0).id());
    assertEquals("body", document.definition().getComponents().get(1).id());
    MenuComponentData close = document.definition().getComponents().get(2);
    assertEquals("close", close.id());
    assertTrue(close.data() instanceof ButtonComponentData);
    assertFalse(((ButtonComponentData) close.data()).actions().isEmpty());
  }

  @Test
  public void simpleHologramContainsOnlyTheRequestedTextDecoration() {
    MenuDocument document = MenuDocumentParser.parse(
        "spawn/welcome", MenuBaselines.simpleHologramSource("spawn/welcome", "<gold>Welcome home"));

    assertEquals(1, document.definition().getComponents().size());
    MenuComponentData content = document.definition().getComponents().getFirst();
    assertEquals("content", content.id());
    assertTrue(content.data() instanceof DecoComponentData);
    DecoComponentData decoration = (DecoComponentData) content.data();
    assertTrue(decoration.iconData() instanceof TextIconData);
    assertEquals("<gold>Welcome home", ((TextIconData) decoration.iconData()).text());
  }

  @Test
  public void simpleHologramFallsBackToItsIdWithoutAddingAButton() {
    MenuDocument document = MenuDocumentParser.parse(
        "spawn/welcome", MenuBaselines.simpleHologramSource("spawn/welcome", null));

    assertEquals(1, document.definition().getComponents().size());
    MenuComponentData content = document.definition().getComponents().getFirst();
    DecoComponentData decoration = (DecoComponentData) content.data();
    assertEquals("&fspawn/welcome", ((TextIconData) decoration.iconData()).text());
    assertFalse(content.data() instanceof ButtonComponentData);
  }

  @Test
  public void simpleHologramPreservesLiteralAsteriskText() {
    MenuDocument document = MenuDocumentParser.parse(
        "spawn/star", MenuBaselines.simpleHologramSource("spawn/star", "*"));

    MenuComponentData content = document.definition().getComponents().getFirst();
    DecoComponentData decoration = (DecoComponentData) content.data();
    assertEquals("*", ((TextIconData) decoration.iconData()).text());
  }
}
