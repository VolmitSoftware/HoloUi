package art.arcane.holoui.api;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HoloTextTest {

  @Test
  public void sanitizeIdKeepsOnlyPortableCharactersAndCapsLength() {
    assertEquals("shop.buybutton-1", HoloText.sanitizeId("shop.buy button-1"));
    assertEquals("etcpasswd", HoloText.sanitizeId("/etc/passwd"));
    assertEquals("dropall", HoloText.sanitizeId("drop\u0000all"));
    assertEquals("admin..open", HoloText.sanitizeId("admin/../open"));
    assertEquals(HoloText.MAX_ID_LENGTH, HoloText.sanitizeId("a".repeat(200)).length());
  }

  @Test
  public void sanitizeIdRejectsAnIdWithNoUsableCharacters() {
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizeId("///"));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizeId(""));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizeId(null));
  }

  @Test
  public void sanitizeMarkupNeutralisesControlCharactersButKeepsNewlines() {
    assertEquals("line one\nline two", HoloText.sanitizeMarkup("line one\nline two"));
    assertEquals("a b", HoloText.sanitizeMarkup("a\u0007b"));
    assertEquals("a b", HoloText.sanitizeMarkup("a\u007Fb"));
    assertEquals("", HoloText.sanitizeMarkup(null));
    assertEquals(HoloText.MAX_MARKUP_LENGTH, HoloText.sanitizeMarkup("x".repeat(9000)).length());
  }

  @Test
  public void sanitizePathRefusesToEscapeTheImagesFolder() {
    assertEquals("icons/buy.png", HoloText.sanitizePath("icons\\buy.png"));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizePath("../../server.properties"));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizePath("icons/../../secret.png"));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizePath("/etc/passwd"));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizePath("C:\\windows\\system32"));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizePath("  "));
    assertThrows(IllegalArgumentException.class, () -> HoloText.sanitizePath("x".repeat(400)));
  }

  @Test
  public void requireDistinctIdsRejectsDuplicatesAndNulls() {
    HoloComponent first = HoloComponent.decoration("a", 0.0D, 0.0D, 0.0D, HoloIcon.text("a"));
    HoloComponent second = HoloComponent.decoration("a", 0.0D, 0.0D, 0.0D, HoloIcon.text("b"));

    IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
        () -> HoloText.requireDistinctIds(List.of(first, second)));
    assertTrue(duplicate.getMessage(), duplicate.getMessage().contains("duplicate component id: a"));

    List<HoloComponent> withNull = new java.util.ArrayList<>();
    withNull.add(null);
    assertThrows(IllegalArgumentException.class, () -> HoloText.requireDistinctIds(withNull));
  }
}
