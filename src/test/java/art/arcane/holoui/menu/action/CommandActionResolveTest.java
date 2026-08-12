package art.arcane.holoui.menu.action;

import art.arcane.holoui.config.action.CommandActionData;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandActionResolveTest {

  @Test
  public void missingAndEmptyCommandsAreDroppedDuringResolution() {
    assertTrue(MenuAction.resolve(List.of(new CommandActionData(null, null, null)), "missing-command", "button").isEmpty());
    assertTrue(MenuAction.resolve(List.of(new CommandActionData(null, "   ", null)), "blank-command", "button").isEmpty());
    assertTrue(MenuAction.resolve(List.of(new CommandActionData(null, "/", null)), "slash-command", "button").isEmpty());
  }

  @Test
  public void usableCommandsRemainInDeclarationOrder() {
    List<MenuAction<?>> actions = MenuAction.resolve(List.of(
        new CommandActionData(null, " /first ", null),
        new CommandActionData(null, "second", null)), "valid-command", "button");

    assertEquals(2, actions.size());
    assertTrue(actions.stream().allMatch(CommandMenuAction.class::isInstance));
  }
}
