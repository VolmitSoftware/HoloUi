package art.arcane.holoui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HoloBoardsCommandPaginationTest {
  @Test
  public void pageCountUsesTenEntriesAndKeepsOneEmptyPage() {
    assertEquals(1, HoloBoardsCommand.pageCount(0));
    assertEquals(1, HoloBoardsCommand.pageCount(10));
    assertEquals(2, HoloBoardsCommand.pageCount(11));
    assertEquals(3, HoloBoardsCommand.pageCount(30));
    assertEquals(214748365, HoloBoardsCommand.pageCount(Integer.MAX_VALUE));
  }

  @Test
  public void pageWindowBoundsEveryPageToTheListSize() {
    assertEquals(new HoloBoardsCommand.PageWindow(2, 3, 10, 20),
        HoloBoardsCommand.pageWindow(23, 2));
    assertEquals(new HoloBoardsCommand.PageWindow(3, 3, 20, 23),
        HoloBoardsCommand.pageWindow(23, 3));
    assertEquals(new HoloBoardsCommand.PageWindow(1, 1, 0, 0),
        HoloBoardsCommand.pageWindow(0, 1));
  }

  @Test
  public void pageWindowRejectsInvalidCountsAndPages() {
    assertThrows(IllegalArgumentException.class, () -> HoloBoardsCommand.pageCount(-1));
    assertThrows(IllegalArgumentException.class, () -> HoloBoardsCommand.pageWindow(23, 0));
    assertThrows(IllegalArgumentException.class, () -> HoloBoardsCommand.pageWindow(23, 4));
  }
}
