package art.arcane.holoui.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class HoloUiCommandNormalizeArgsTest {

  @Test
  public void bareOpenRewritesToKeyedMenu() {
    assertArrayEquals(
        new String[]{"open", "menu=shop"},
        HoloUiCommandService.normalizeArgs(new String[]{"open", "shop"}));
  }

  @Test
  public void bareOpenStarRewritesToKeyedMenu() {
    assertArrayEquals(
        new String[]{"open", "menu=*"},
        HoloUiCommandService.normalizeArgs(new String[]{"open", "*"}));
  }

  @Test
  public void keyedOpenIsUnchanged() {
    assertArrayEquals(
        new String[]{"open", "menu=shop"},
        HoloUiCommandService.normalizeArgs(new String[]{"open", "menu=shop"}));
  }

  @Test
  public void openHelpIsNotRewrittenAsMenu() {
    assertArrayEquals(
        new String[]{"open", "help"},
        HoloUiCommandService.normalizeArgs(new String[]{"open", "help"}));
  }

  @Test
  public void barePreviewsResetRewritesToKeyedName() {
    assertArrayEquals(
        new String[]{"previews", "reset", "name=chest"},
        HoloUiCommandService.normalizeArgs(new String[]{"previews", "reset", "chest"}));
  }

  @Test
  public void barePreviewsAloneBecomesList() {
    assertArrayEquals(
        new String[]{"previews", "list"},
        HoloUiCommandService.normalizeArgs(new String[]{"previews"}));
  }

  @Test
  public void previewsDumpBareNameIsUnchanged() {
    assertArrayEquals(
        new String[]{"previews", "dump", "chest"},
        HoloUiCommandService.normalizeArgs(new String[]{"previews", "dump", "chest"}));
  }

  @Test
  public void bareOpenTabPrefixRewritesToKeyedMenu() {
    assertArrayEquals(
        new String[]{"open", "menu=sh"},
        HoloUiCommandService.normalizeTabArgs(new String[]{"open", "sh"}));
    assertArrayEquals(
        new String[]{"open", "menu="},
        HoloUiCommandService.normalizeTabArgs(new String[]{"open", ""}));
  }

  @Test
  public void barePreviewResetTabPrefixRewritesToKeyedName() {
    assertArrayEquals(
        new String[]{"previews", "reset", "name=ch"},
        HoloUiCommandService.normalizeTabArgs(new String[]{"previews", "reset", "ch"}));
  }

  @Test
  public void positionalTabSuggestionsAreReturnedAsBareValues() {
    assertEquals(
        List.of("shop", "showcase"),
        HoloUiCommandService.restorePositionalSuggestions(
            new String[]{"open", "sh"}, List.of("menu=shop", "menu=showcase")));
    assertEquals(
        List.of("chest"),
        HoloUiCommandService.restorePositionalSuggestions(
            new String[]{"previews", "reset", "ch"}, List.of("name=chest")));
  }

  @Test
  public void keyedTabSuggestionsRemainKeyed() {
    assertEquals(
        List.of("menu=shop"),
        HoloUiCommandService.restorePositionalSuggestions(
            new String[]{"open", "menu=sh"}, List.of("menu=shop")));
  }
}
