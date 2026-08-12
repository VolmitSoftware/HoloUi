package art.arcane.holoui.config.menu;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ButtonComponentData;
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
}
