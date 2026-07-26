package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloComponent;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenu;
import art.arcane.holoui.api.HoloMenuHandle;
import art.arcane.holoui.api.HoloMenuState;
import art.arcane.holoui.config.MenuDefinitionData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HoloUiServiceImplTest {
  private static final String MENU_ID = "example-shop";

  private final UUID playerId = UUID.randomUUID();
  private final AtomicBoolean online = new AtomicBoolean(true);
  private final AtomicBoolean permitted = new AtomicBoolean(true);
  private final Player player = ApiTestSupport.player(playerId, online, permitted);
  private final ApiTestSupport.CapturingLogger logger = ApiTestSupport.logger();
  private final FakeBackend backend = new FakeBackend(player);
  private final Plugin holoUi = ApiTestSupport.plugin("HoloUi", () -> true);
  private final Plugin owner = ApiTestSupport.plugin("ExampleShop", () -> true);
  private final HoloUiServiceImpl service = new HoloUiServiceImpl(holoUi, logger.logger(), backend);

  @Test
  public void aDescriptorThatThrowsWhileTranslatingNeverRegistersAHandle() {
    HoloMenu menu = HoloMenu.builder()
        .id(MENU_ID)
        .component(HoloComponent.decoration("icon", 0.0D, 0.0D, 0.0D, HoloIcon.item(new HostileStack())))
        .build();

    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        service.open(owner, player, menu);
        fail("expected the hostile ItemStack clone to propagate to the caller");
      } catch (IllegalStateException expected) {
        assertEquals("hostile clone", expected.getMessage());
      }
    }

    assertEquals(0, service.openHandleCount());
    assertEquals(0, backend.scheduled());
  }

  @Test
  public void aDescriptorThatTranslatesCleanlyStillOpensAfterAFailedAttempt() {
    HoloMenu hostile = HoloMenu.builder()
        .id(MENU_ID)
        .component(HoloComponent.decoration("icon", 0.0D, 0.0D, 0.0D, HoloIcon.item(new HostileStack())))
        .build();
    HoloMenu healthy = HoloMenu.builder()
        .id(MENU_ID)
        .component(HoloComponent.decoration("title", 0.0D, 0.0D, 0.0D, HoloIcon.text("<red>Shop")))
        .build();

    try {
      service.open(owner, player, hostile);
      fail("expected the hostile ItemStack clone to propagate to the caller");
    } catch (IllegalStateException ignored) {
      assertEquals(0, service.openHandleCount());
    }

    HoloMenuHandle handle = service.open(owner, player, healthy);

    assertEquals(HoloMenuState.OPEN, handle.state());
    assertEquals(1, service.openHandleCount());
  }

  @Test
  public void anIsEnabledProbeThatThrowsInsideTheSchedulerTaskFailsTheHandleInsteadOfEscaping() {
    AtomicInteger probes = new AtomicInteger();
    Plugin broken = ApiTestSupport.plugin("BrokenPlugin", () -> {
      if (probes.incrementAndGet() > 1) {
        throw new IllegalStateException("classloader is gone");
      }

      return true;
    });

    HoloMenuHandle handle = service.open(broken, player, MENU_ID);

    assertTrue(backend.escaped().toString(), backend.escaped().isEmpty());
    assertEquals(HoloMenuState.FAILED, handle.state());
    assertEquals(0, service.openHandleCount());
    assertTrue(logger.messagesAt(Level.SEVERE).toString(),
        logger.messagesAt(Level.SEVERE).stream().anyMatch(message -> message.contains("BrokenPlugin")));
  }

  @Test
  public void aMenuLookupThatThrowsInsideTheSchedulerTaskFailsTheHandleInsteadOfEscaping() {
    backend.menuLookupThrows();

    HoloMenuHandle handle = service.open(owner, player, MENU_ID);

    assertTrue(backend.escaped().toString(), backend.escaped().isEmpty());
    assertEquals(HoloMenuState.FAILED, handle.state());
    assertEquals(0, service.openHandleCount());
  }

  @Test
  public void aRetiredEntitySchedulerTaskStillTerminatesThePendingOpenHandle() {
    backend.retireEveryTask();
    List<HoloCloseReason> closed = new ArrayList<>();

    HoloMenuHandle handle = service.open(owner, player, MENU_ID).onClosed(closed::add);

    assertEquals(HoloMenuState.CLOSED, handle.state());
    assertEquals(List.of(HoloCloseReason.QUIT), closed);
    assertEquals(0, service.openHandleCount());
  }

  @Test
  public void aRetiredEntitySchedulerTaskStillTerminatesAHandleWaitingOnItsClose() {
    HoloMenuHandle handle = service.open(owner, player, MENU_ID);
    assertEquals(HoloMenuState.OPEN, handle.state());

    List<HoloCloseReason> closed = new ArrayList<>();
    handle.onClosed(closed::add);
    backend.retireEveryTask();
    handle.close();

    assertEquals(HoloMenuState.CLOSED, handle.state());
    assertEquals(List.of(HoloCloseReason.CLOSED_BY_OWNER), closed);
    assertEquals(0, service.openHandleCount());
  }

  @Test
  public void aSuccessfulOpenRegistersOneLiveHandleAndDeregistersItWhenTheSessionCloses() {
    List<HoloCloseReason> closed = new ArrayList<>();
    HoloMenuHandle handle = service.open(owner, player, MENU_ID).onClosed(closed::add);

    assertEquals(HoloMenuState.OPEN, handle.state());
    assertEquals(1, service.openHandleCount());
    assertEquals(1, backend.opened().size());
    assertSame(handle, backend.opened().get(0));

    handle.close();

    assertEquals(HoloMenuState.CLOSED, handle.state());
    assertEquals(List.of(HoloCloseReason.CLOSED_BY_OWNER), closed);
    assertEquals(0, service.openHandleCount());
  }

  @Test
  public void aRefusedScheduleFailsTheHandleWithoutRetainingIt() {
    backend.refuseEveryTask();

    HoloMenuHandle handle = service.open(owner, player, MENU_ID);

    assertEquals(HoloMenuState.FAILED, handle.state());
    assertEquals(0, service.openHandleCount());
    assertEquals(0, backend.opened().size());
  }

  @Test
  public void aPlayerWhoLeavesBeforeTheTaskRunsResolvesTheHandleToQuit() {
    backend.deferEveryTask();
    List<HoloCloseReason> closed = new ArrayList<>();
    HoloMenuHandle handle = service.open(owner, player, MENU_ID).onClosed(closed::add);

    assertEquals(HoloMenuState.PENDING, handle.state());
    online.set(false);
    backend.runDeferred();

    assertEquals(HoloMenuState.CLOSED, handle.state());
    assertEquals(List.of(HoloCloseReason.QUIT), closed);
    assertEquals(0, service.openHandleCount());
  }

  @Test
  public void openingByIdWithoutThePermissionIsDeniedAndRetainsNothing() {
    permitted.set(false);

    HoloMenuHandle handle = service.open(owner, player, MENU_ID);

    assertEquals(HoloMenuState.FAILED, handle.state());
    assertEquals(0, service.openHandleCount());
    assertEquals(0, backend.opened().size());
  }

  @Test
  public void openingForAnOwnerThatIsAlreadyDisabledNeverReachesTheScheduler() {
    Plugin disabled = ApiTestSupport.plugin("DisabledPlugin", () -> false);

    HoloMenuHandle handle = service.open(disabled, player, MENU_ID);

    assertEquals(HoloMenuState.CLOSED, handle.state());
    assertEquals(0, service.openHandleCount());
    assertEquals(0, backend.scheduled());
  }

  @Test
  public void closingAPlayerWithNoSessionIsRefusedWithoutTouchingTheScheduler() {
    assertFalse(service.close(player));
    assertEquals(0, backend.scheduled());
    assertFalse(service.isOpen(player));

    service.open(owner, player, MENU_ID);

    assertTrue(service.isOpen(player));
    assertTrue(service.close(player));
    assertEquals(Set.of(MENU_ID), service.menuIds());
  }

  private static final class HostileStack extends ItemStack {
    private int clones;

    @Override
    public ItemStack clone() {
      if (clones++ == 0) {
        return this;
      }

      throw new IllegalStateException("hostile clone");
    }
  }

  private static final class FakeBackend implements ApiBackend {
    private final Player player;
    private final Map<String, MenuDefinitionData> menus = new LinkedHashMap<>();
    private final List<ApiMenuHandle> opened = new ArrayList<>();
    private final List<Throwable> escaped = new ArrayList<>();
    private final List<Runnable> deferred = new ArrayList<>();

    private Dispatch dispatch = Dispatch.INLINE;
    private boolean lookupThrows;
    private int scheduled;

    private FakeBackend(Player player) {
      this.player = player;
      menus.put(MENU_ID, ApiMenuTranslator.definition(HoloMenu.builder()
          .id(MENU_ID)
          .component(HoloComponent.decoration("title", 0.0D, 0.0D, 0.0D, HoloIcon.text("<red>Shop")))
          .build()));
    }

    private void retireEveryTask() {
      dispatch = Dispatch.RETIRE;
    }

    private void refuseEveryTask() {
      dispatch = Dispatch.REFUSE;
    }

    private void deferEveryTask() {
      dispatch = Dispatch.DEFER;
    }

    private void menuLookupThrows() {
      lookupThrows = true;
    }

    private void runDeferred() {
      List<Runnable> pending = new ArrayList<>(deferred);
      deferred.clear();
      pending.forEach(this::guarded);
    }

    private List<ApiMenuHandle> opened() {
      return opened;
    }

    private List<Throwable> escaped() {
      return escaped;
    }

    private int scheduled() {
      return scheduled;
    }

    @Override
    public boolean schedule(Player target, Runnable task, Runnable retired) {
      scheduled++;

      return switch (dispatch) {
        case INLINE -> {
          guarded(task);
          yield true;
        }
        case RETIRE -> {
          if (retired != null) {
            guarded(retired);
          }

          yield true;
        }
        case DEFER -> {
          deferred.add(task);
          yield true;
        }
        case REFUSE -> false;
      };
    }

    @Override
    public boolean hasSession(Player target) {
      return !opened.isEmpty();
    }

    @Override
    public boolean openSession(Player target, MenuDefinitionData definition, ApiMenuHandle handle) {
      opened.add(handle);
      handle.markOpen();
      return true;
    }

    @Override
    public boolean closeSession(Player target, HoloCloseReason reason) {
      List<ApiMenuHandle> closing = new ArrayList<>(opened);
      opened.clear();
      closing.forEach(handle -> handle.terminate(reason));
      return !closing.isEmpty();
    }

    @Override
    public boolean closeSessionOf(Player target, ApiMenuHandle handle, HoloCloseReason reason) {
      if (!opened.remove(handle)) {
        return false;
      }

      handle.terminate(reason);
      return true;
    }

    @Override
    public void forgetClickGuard(String ownerName) {
    }

    @Override
    public MenuDefinitionData definition(String menuId) {
      if (lookupThrows) {
        throw new IllegalStateException("menu registry is gone");
      }

      return menus.get(menuId);
    }

    @Override
    public Set<String> menuIds() {
      return Set.copyOf(menus.keySet());
    }

    @Override
    public Player online(UUID target) {
      return player;
    }

    private void guarded(Runnable task) {
      try {
        task.run();
      } catch (Throwable error) {
        escaped.add(error);
      }
    }

    private enum Dispatch {
      INLINE, RETIRE, DEFER, REFUSE
    }
  }
}
