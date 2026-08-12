package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloClick;
import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenuHandle;
import art.arcane.holoui.api.HoloMenuState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ApiClickGuardTest {

  private final Player player = ApiTestSupport.player(UUID.randomUUID());
  private final HoloClick click = new HoloClick(
      player, "shop", "buy", HoloClickTrigger.SHIFT_RIGHT_CLICK, new InertHandle());

  @Test
  public void clickCarriesTheExactInteractionTrigger() {
    assertEquals(HoloClickTrigger.SHIFT_RIGHT_CLICK, click.trigger());
  }

  @Test
  public void clickRejectsANullInteractionTrigger() {
    assertThrows(NullPointerException.class,
        () -> new HoloClick(player, "shop", "buy", null, new InertHandle()));
  }

  @Test
  public void aThrowingHandlerIsCountedAndLoggedRatherThanPropagating() {
    ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
    ApiClickGuard guard = new ApiClickGuard(logger.logger(), () -> 0L, 5, 0L);
    ApiOwner owner = new ApiOwner("HostilePlugin", () -> true);

    ApiClickOutcome outcome = guard.dispatch(owner, handlerThatThrows(), click);

    assertEquals(ApiClickOutcome.FAULTED, outcome);
    assertEquals(1L, guard.faults());
    assertTrue(logger.messagesAt(Level.WARNING).toString(),
        logger.messagesAt(Level.WARNING).stream().anyMatch(message -> message.contains("HostilePlugin")));
  }

  @Test
  public void aHandlerThatThrowsAnErrorRatherThanAnExceptionIsStillContained() {
    ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
    ApiClickGuard guard = new ApiClickGuard(logger.logger(), () -> 0L, 5, 0L);
    ApiOwner owner = new ApiOwner("HostilePlugin", () -> true);

    ApiClickOutcome outcome = guard.dispatch(owner, ignored -> {
      throw new StackOverflowError("recursive handler");
    }, click);

    assertEquals(ApiClickOutcome.FAULTED, outcome);
    assertEquals(1L, guard.faults());
    assertTrue(logger.messagesAt(Level.WARNING).toString(),
        logger.messagesAt(Level.WARNING).stream().anyMatch(message -> message.contains("HostilePlugin")));
  }

  @Test
  public void anOwnerIsQuarantinedAfterTheFaultLimitAndSkippedFromThenOn() {
    ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
    ApiClickGuard guard = new ApiClickGuard(logger.logger(), () -> 0L, 3, 0L);
    ApiOwner owner = new ApiOwner("HostilePlugin", () -> true);
    AtomicInteger invocations = new AtomicInteger();
    HoloClickHandler handler = ignored -> {
      invocations.incrementAndGet();
      throw new IllegalStateException("boom");
    };

    assertEquals(ApiClickOutcome.FAULTED, guard.dispatch(owner, handler, click));
    assertEquals(ApiClickOutcome.FAULTED, guard.dispatch(owner, handler, click));
    assertEquals(ApiClickOutcome.FAULTED, guard.dispatch(owner, handler, click));
    assertTrue(guard.quarantined("HostilePlugin"));

    assertEquals(ApiClickOutcome.SKIPPED_QUARANTINED, guard.dispatch(owner, handler, click));
    assertEquals(ApiClickOutcome.SKIPPED_QUARANTINED, guard.dispatch(owner, handler, click));

    assertEquals(3, invocations.get());
    assertEquals(1L, guard.quarantines());
    assertEquals(2L, guard.skips());
    assertTrue(logger.messagesAt(Level.SEVERE).toString(),
        logger.messagesAt(Level.SEVERE).stream().anyMatch(message -> message.contains("HostilePlugin")));
  }

  @Test
  public void handlersBelongingToADisabledOwnerAreNeverInvoked() {
    ApiClickGuard guard = new ApiClickGuard(ApiTestSupport.logger().logger(), () -> 0L, 5, 0L);
    AtomicBoolean enabled = new AtomicBoolean(true);
    ApiOwner owner = new ApiOwner("ExampleShop", enabled::get);
    AtomicInteger invocations = new AtomicInteger();
    HoloClickHandler handler = ignored -> invocations.incrementAndGet();

    assertEquals(ApiClickOutcome.DISPATCHED, guard.dispatch(owner, handler, click));
    enabled.set(false);
    assertEquals(ApiClickOutcome.SKIPPED_OWNER_DISABLED, guard.dispatch(owner, handler, click));

    assertEquals(1, invocations.get());
  }

  @Test
  public void anOwnerWhoseIsEnabledProbeThrowsIsCountedAndLoggedRatherThanSwallowed() {
    ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
    ApiClickGuard guard = new ApiClickGuard(logger.logger(), () -> 0L, 5, 0L);
    ApiOwner owner = new ApiOwner("BrokenPlugin", () -> {
      throw new IllegalStateException("classloader is gone");
    });
    AtomicInteger invocations = new AtomicInteger();

    assertEquals(ApiClickOutcome.FAULTED,
        guard.dispatch(owner, ignored -> invocations.incrementAndGet(), click));
    assertEquals(0, invocations.get());
    assertEquals(1L, guard.faults());
    assertTrue(logger.messagesAt(Level.WARNING).toString(),
        logger.messagesAt(Level.WARNING).stream().anyMatch(message -> message.contains("BrokenPlugin")));
  }

  @Test
  public void forgettingAnOwnerClearsQuarantineSoAReenabledPluginWorksAgain() {
    ApiClickGuard guard = new ApiClickGuard(ApiTestSupport.logger().logger(), () -> 0L, 1, 0L);
    ApiOwner owner = new ApiOwner("HostilePlugin", () -> true);

    guard.dispatch(owner, handlerThatThrows(), click);
    assertTrue(guard.quarantined("HostilePlugin"));

    guard.forget("HostilePlugin");

    assertFalse(guard.quarantined("HostilePlugin"));
    assertEquals(ApiClickOutcome.DISPATCHED, guard.dispatch(owner, ignored -> {
    }, click));
  }

  @Test
  public void aSlowHandlerWarnsOncePerIntervalAndStillCountsAsDispatched() {
    ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
    AtomicLong now = new AtomicLong();
    ApiClickGuard guard = new ApiClickGuard(logger.logger(), now::get, 5, 5L);
    ApiOwner owner = new ApiOwner("SlowPlugin", () -> true);
    HoloClickHandler handler = ignored -> now.addAndGet(50L);

    assertEquals(ApiClickOutcome.DISPATCHED, guard.dispatch(owner, handler, click));
    assertEquals(ApiClickOutcome.DISPATCHED, guard.dispatch(owner, handler, click));

    assertEquals(logger.messages().toString(), 1L,
        logger.messagesAt(Level.WARNING).stream().filter(message -> message.contains("SlowPlugin")).count());
    assertEquals(2L, guard.dispatches());
    assertEquals(0L, guard.faults());
  }

  @Test
  public void aNullHandlerIsReportedRatherThanDispatched() {
    ApiClickGuard guard = new ApiClickGuard(ApiTestSupport.logger().logger(), () -> 0L, 5, 0L);
    assertEquals(ApiClickOutcome.NO_HANDLER,
        guard.dispatch(new ApiOwner("ExampleShop", () -> true), null, click));
    assertEquals(0L, guard.dispatches());
  }

  private static HoloClickHandler handlerThatThrows() {
    return ignored -> {
      throw new IllegalStateException("boom");
    };
  }

  private static final class InertHandle implements HoloMenuHandle {
    private final UUID id = UUID.randomUUID();

    @Override
    public UUID sessionId() {
      return id;
    }

    @Override
    public UUID playerId() {
      return id;
    }

    @Override
    public String menuId() {
      return "shop";
    }

    @Override
    public HoloMenuState state() {
      return HoloMenuState.OPEN;
    }

    @Override
    public boolean setText(String componentId, String miniMessage) {
      return false;
    }

    @Override
    public boolean setItem(String componentId, ItemStack stack) {
      return false;
    }

    @Override
    public boolean setIcon(String componentId, HoloIcon icon) {
      return false;
    }

    @Override
    public void close() {
    }

    @Override
    public HoloMenuHandle onClosed(Consumer<HoloCloseReason> callback) {
      return this;
    }
  }
}
