package art.arcane.holoui.menu.action;

import art.arcane.holoui.config.action.CommandActionData;
import art.arcane.holoui.config.action.MenuActionData;
import art.arcane.holoui.enums.MenuActionCommandSource;
import art.arcane.holoui.enums.MenuActionType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MenuActionResolveTest {

  @Test
  public void resolveSkipsUnsupportedActionTypesInsteadOfAddingNull() {
    List<MenuActionData> declared = new ArrayList<>();
    declared.add(new CommandActionData(MenuActionCommandSource.GLOBAL, "/say hi"));
    declared.add(new UnknownActionData());
    declared.add(null);

    List<MenuAction<?>> resolved = MenuAction.resolve(declared, "shop", "buy");

    assertEquals(1, resolved.size());
    assertTrue(resolved.get(0) instanceof CommandMenuAction);
    assertTrue(resolved.stream().noneMatch(java.util.Objects::isNull));
  }

  @Test
  public void resolveKeepsKnownActionsInDeclarationOrderAndToleratesNullLists() {
    List<MenuActionData> declared = List.of(
        new CommandActionData(MenuActionCommandSource.GLOBAL, "/first"),
        new CommandActionData(MenuActionCommandSource.PLAYER, "/second"));

    List<MenuAction<?>> resolved = MenuAction.resolve(declared, "shop", "buy");

    assertEquals(2, resolved.size());
    assertTrue(MenuAction.resolve(null, "shop", "buy").isEmpty());
    assertTrue(MenuAction.resolve(List.of(), "shop", "buy").isEmpty());
  }

  private static final class UnknownActionData implements MenuActionData {
    @Override
    public MenuActionType getType() {
      return MenuActionType.COMMAND;
    }
  }
}
