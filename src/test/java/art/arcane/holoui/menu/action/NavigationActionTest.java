package art.arcane.holoui.menu.action;

import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.config.action.CommandActionData;
import art.arcane.holoui.config.action.NavigationActionData;
import art.arcane.holoui.enums.NavigationMode;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NavigationActionTest {
  @Test
  public void targetNavigationIsResolvedAndStopsTheRemainingActionChain() {
    List<MenuAction<?>> actions = MenuAction.resolve(List.of(
        new NavigationActionData("shops/confirm", NavigationMode.REPLACE, null)
    ), "shops/root", "buy");
    AtomicReference<NavigationRequest> request = new AtomicReference<>();

    ActionOutcome outcome = MenuAction.execute(actions, context(request, HoloClickTrigger.LEFT_CLICK));

    assertEquals(ActionOutcome.STOP, outcome);
    assertEquals(new NavigationRequest(NavigationMode.REPLACE, "shops/confirm"), request.get());
  }

  @Test
  public void targetlessPushAndReplaceActionsAreDroppedDuringResolution() {
    assertTrue(MenuAction.resolve(List.of(
        new NavigationActionData(" ", NavigationMode.PUSH, null),
        new NavigationActionData(null, NavigationMode.REPLACE, null)
    ), "shops/root", "broken").isEmpty());
  }

  @Test
  public void backNavigationDoesNotRequireATarget() {
    AtomicReference<NavigationRequest> request = new AtomicReference<>();
    NavigateMenuAction action = new NavigateMenuAction(new NavigationActionData(null, NavigationMode.BACK, null));

    assertEquals(ActionOutcome.STOP, action.execute(context(request, HoloClickTrigger.LEFT_CLICK)));
    assertEquals(new NavigationRequest(NavigationMode.BACK, null), request.get());
  }

  @Test
  public void navigationStopsOnlyTheActionsMatchingTheCurrentTrigger() {
    AtomicReference<NavigationRequest> request = new AtomicReference<>();
    RecordingAction any = new RecordingAction(HoloClickTrigger.ANY);
    NavigateMenuAction rightNavigation = new NavigateMenuAction(
        new NavigationActionData("shops/right", NavigationMode.REPLACE, HoloClickTrigger.RIGHT_CLICK));
    RecordingAction leftAfterNavigation = new RecordingAction(HoloClickTrigger.LEFT_CLICK);
    List<MenuAction<?>> actions = List.of(any, rightNavigation, leftAfterNavigation);

    assertEquals(ActionOutcome.CONTINUE,
        MenuAction.execute(actions, context(request, HoloClickTrigger.LEFT_CLICK)));
    assertEquals(1, any.executions);
    assertEquals(1, leftAfterNavigation.executions);
    assertNull(request.get());

    assertEquals(ActionOutcome.STOP,
        MenuAction.execute(actions, context(request, HoloClickTrigger.RIGHT_CLICK)));
    assertEquals(2, any.executions);
    assertEquals(1, leftAfterNavigation.executions);
    assertEquals(new NavigationRequest(NavigationMode.REPLACE, "shops/right"), request.get());
  }

  private static ActionContext context(AtomicReference<NavigationRequest> request, HoloClickTrigger trigger) {
    return new ActionContext() {
      @Override
      public Player player() {
        return null;
      }

      @Override
      public String menuId() {
        return "shops/root";
      }

      @Override
      public String componentId() {
        return "buy";
      }

      @Override
      public HoloClickTrigger trigger() {
        return trigger;
      }

      @Override
      public NavigationResult navigate(NavigationRequest navigation) {
        request.set(navigation);
        return NavigationResult.APPLIED;
      }
    };
  }

  private static final class RecordingAction extends MenuAction<CommandActionData> {
    private int executions;

    private RecordingAction(HoloClickTrigger trigger) {
      super(new CommandActionData(null, "test", trigger));
    }

    @Override
    public ActionOutcome execute(ActionContext context) {
      executions++;
      return ActionOutcome.CONTINUE;
    }
  }
}
