package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloMenuState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiHandleStateTest {

  @Test
  public void terminationIsFinalAndASecondTerminationIsANoOp() {
    ApiHandleState state = new ApiHandleState(ApiTestSupport.logger().logger(), "ExamplePlugin");
    AtomicInteger fired = new AtomicInteger();
    state.onClosed(reason -> fired.incrementAndGet());

    assertEquals(HoloMenuState.PENDING, state.state());
    assertTrue(state.markOpen());
    assertEquals(HoloMenuState.OPEN, state.state());

    assertTrue(state.terminate(HoloCloseReason.REPLACED, false));
    assertFalse(state.terminate(HoloCloseReason.QUIT, false));
    assertFalse(state.terminate(HoloCloseReason.OPEN_FAILED, true));

    assertEquals(HoloMenuState.CLOSED, state.state());
    assertEquals(HoloCloseReason.REPLACED, state.reason());
    assertEquals(1, fired.get());
    assertFalse(state.live());
    assertFalse(state.markOpen());
  }

  @Test
  public void aCallbackRegisteredOnAnAlreadyTerminalHandleFiresImmediately() {
    ApiHandleState state = new ApiHandleState(ApiTestSupport.logger().logger(), "ExamplePlugin");
    state.terminate(HoloCloseReason.HOLOUI_SHUTDOWN, false);

    List<HoloCloseReason> seen = new ArrayList<>();
    state.onClosed(seen::add);

    assertEquals(List.of(HoloCloseReason.HOLOUI_SHUTDOWN), seen);

    state.onClosed(seen::add);
    assertEquals(List.of(HoloCloseReason.HOLOUI_SHUTDOWN, HoloCloseReason.HOLOUI_SHUTDOWN), seen);
  }

  @Test
  public void aLaterCallbackReplacesTheEarlierOneAndOnlyTheLatestIsCalled() {
    ApiHandleState state = new ApiHandleState(ApiTestSupport.logger().logger(), "ExamplePlugin");
    AtomicInteger first = new AtomicInteger();
    AtomicInteger second = new AtomicInteger();

    state.onClosed(reason -> first.incrementAndGet());
    state.onClosed(reason -> second.incrementAndGet());
    state.terminate(HoloCloseReason.DEATH, false);

    assertEquals(0, first.get());
    assertEquals(1, second.get());
  }

  @Test
  public void aThrowingCloseCallbackIsLoggedWithThePluginNameAndNeverPropagates() {
    ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
    ApiHandleState state = new ApiHandleState(logger.logger(), "HostilePlugin");
    state.onClosed(reason -> {
      throw new IllegalStateException("boom");
    });

    assertTrue(state.terminate(HoloCloseReason.TELEPORT, false));
    assertEquals(HoloMenuState.CLOSED, state.state());
    assertTrue(logger.messagesAt(Level.WARNING).toString(),
        logger.messagesAt(Level.WARNING).stream().anyMatch(message -> message.contains("HostilePlugin")));
  }

  @Test
  public void aFailedTerminationLandsInFailedRatherThanClosed() {
    ApiHandleState state = new ApiHandleState(ApiTestSupport.logger().logger(), "ExamplePlugin");

    assertTrue(state.terminate(HoloCloseReason.OPEN_FAILED, true));
    assertEquals(HoloMenuState.FAILED, state.state());
    assertEquals(HoloCloseReason.OPEN_FAILED, state.reason());
    assertTrue(state.state().terminal());
  }
}
