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
  public void bareBoardsAloneBecomesList() {
    assertArrayEquals(
        new String[]{"boards", "list"},
        HoloUiCommandService.normalizeArgs(new String[]{"boards"}));
  }

  @Test
  public void bareBoardsNearRadiusRewritesToKeyedValue() {
    assertArrayEquals(
        new String[]{"boards", "near", "radius=24"},
        HoloUiCommandService.normalizeArgs(new String[]{"boards", "near", "24"}));
    assertArrayEquals(
        new String[]{"boards", "near", "radius=2"},
        HoloUiCommandService.normalizeTabArgs(new String[]{"boards", "near", "2"}));
  }

  @Test
  public void bareBoardsListPageRewritesToKeyedValue() {
    assertArrayEquals(
        new String[]{"boards", "list", "page=2"},
        HoloUiCommandService.normalizeArgs(new String[]{"boards", "list", "2"}));
    assertArrayEquals(
        new String[]{"boards", "list", "page="},
        HoloUiCommandService.normalizeTabArgs(new String[]{"boards", "list", ""}));
  }

  @Test
  public void multiWordRowTextBecomesOneKeyedDirectorArgument() {
    assertArrayEquals(
        new String[]{"menus", "addrow", "shop", "text=<gold>Hello brave world"},
        HoloUiCommandService.normalizeArgs(
            new String[]{"menus", "addrow", "shop", "<gold>Hello", "brave", "world"}));
    assertArrayEquals(
        new String[]{"boards", "setrow", "spawn/info", "2", "text=Click = to continue"},
        HoloUiCommandService.normalizeArgs(
            new String[]{"boards", "setrow", "spawn/info", "2", "text=Click", "=", "to", "continue"}));
  }

  @Test
  public void contentValuesAndImagePathsBecomeOneKeyedDirectorArgument() {
    assertArrayEquals(
        new String[]{"menus", "seticon", "shops/main", "2", "text", "value=<gold>Buy now"},
        HoloUiCommandService.normalizeArgs(
            new String[]{"menus", "seticon", "shops/main", "2", "text", "<gold>Buy", "now"}));
    assertArrayEquals(
        new String[]{"boards", "style", "spawn/info", "1", "backgroundArgb", "value=#CC112233"},
        HoloUiCommandService.normalizeArgs(
            new String[]{"boards", "style", "spawn/info", "1", "backgroundArgb", "#CC112233"}));
    assertArrayEquals(
        new String[]{"boards", "image", "spawn/info", "path=event boards/welcome.png"},
        HoloUiCommandService.normalizeArgs(
            new String[]{"boards", "image", "spawn/info", "event", "boards/welcome.png"}));
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
    assertEquals(
        List.of("24"),
        HoloUiCommandService.restorePositionalSuggestions(
            new String[]{"boards", "near", "2"}, List.of("radius=24")));
    assertEquals(
        List.of("2"),
        HoloUiCommandService.restorePositionalSuggestions(
            new String[]{"boards", "list", "2"}, List.of("page=2")));
  }

  @Test
  public void keyedTabSuggestionsRemainKeyed() {
    assertEquals(
        List.of("menu=shop"),
        HoloUiCommandService.restorePositionalSuggestions(
            new String[]{"open", "menu=sh"}, List.of("menu=shop")));
  }
}
