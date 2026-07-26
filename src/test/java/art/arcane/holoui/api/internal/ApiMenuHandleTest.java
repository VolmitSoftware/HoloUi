package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenuState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ApiMenuHandleTest {

  private final List<HoloCloseReason> closeRequests = new ArrayList<>();
  private final List<ApiMenuHandle> deregistered = new ArrayList<>();
  private final HoloClickHandler buyHandler = click -> {
  };

  @Test
  public void settersRefuseComponentIdsTheDescriptorNeverDeclared() {
    ApiMenuHandle handle = handle();

    assertTrue(handle.setText("stock", "<gray>In stock: 3"));
    assertFalse(handle.setText("does-not-exist", "<gray>hi"));
    assertFalse(handle.setText(null, "<gray>hi"));
    assertFalse(handle.setIcon("stock", null));
    assertFalse(handle.setItem("stock", null));
  }

  @Test
  public void settersRefuseUpdatesOnceTheHandleIsTerminal() {
    ApiMenuHandle handle = handle();
    handle.markOpen();

    assertTrue(handle.setText("stock", "a"));
    assertTrue(handle.terminate(HoloCloseReason.QUIT));

    assertEquals(HoloMenuState.CLOSED, handle.state());
    assertFalse(handle.setText("stock", "b"));
    assertFalse(handle.dirty());
  }

  @Test
  public void repeatedSettersCoalesceIntoASingleDrainedUpdatePerComponent() {
    ApiMenuHandle handle = handle();
    handle.markOpen();

    for (int index = 0; index < 50; index++) {
      handle.setText("stock", "In stock: " + index);
      handle.setIcon("title", HoloIcon.text("Title " + index));
    }

    Map<String, HoloIcon> applied = new LinkedHashMap<>();
    assertTrue(handle.dirty());
    assertEquals(2, handle.drain(applied::put));
    assertEquals(new HoloIcon.Text("In stock: 49"), applied.get("stock"));
    assertEquals(new HoloIcon.Text("Title 49"), applied.get("title"));
    assertFalse(handle.dirty());
  }

  @Test
  public void closeGoesThroughTheCloseRequestSeamExactlyWhileTheHandleIsLive() {
    ApiMenuHandle handle = handle();

    handle.close();
    assertEquals(List.of(HoloCloseReason.CLOSED_BY_OWNER), closeRequests);

    handle.terminate(HoloCloseReason.CLOSED_BY_OWNER);
    handle.close();
    assertEquals(List.of(HoloCloseReason.CLOSED_BY_OWNER), closeRequests);
  }

  @Test
  public void terminationDiscardsPendingUpdatesAndDeregistersTheHandleOnce() {
    ApiMenuHandle handle = handle();
    handle.markOpen();
    handle.setText("stock", "a");
    AtomicInteger closed = new AtomicInteger();
    handle.onClosed(reason -> closed.incrementAndGet());

    assertTrue(handle.terminate(HoloCloseReason.HOLOUI_SHUTDOWN));
    assertFalse(handle.terminate(HoloCloseReason.QUIT));

    assertFalse(handle.dirty());
    assertEquals(0, handle.drain((id, icon) -> {
    }));
    assertEquals(List.of(handle), deregistered);
    assertEquals(1, closed.get());
  }

  @Test
  public void handlersAreExposedByComponentIdAndOnlyForButtons() {
    ApiMenuHandle handle = handle();

    assertSame(buyHandler, handle.handler("buy"));
    assertNull(handle.handler("stock"));
    assertNull(handle.handler("nope"));
    assertEquals("ExampleShop", handle.owner().name());
  }

  private ApiMenuHandle handle() {
    return new ApiMenuHandle(UUID.randomUUID(), "example-shop", new ApiOwner("ExampleShop", () -> true),
        Map.of("buy", buyHandler), Set.of("title", "buy", "stock"), ApiTestSupport.logger().logger(),
        (handle, reason) -> closeRequests.add(reason), deregistered::add);
  }
}
