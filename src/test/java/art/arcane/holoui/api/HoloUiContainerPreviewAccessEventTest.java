package art.arcane.holoui.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HoloUiContainerPreviewAccessEventTest {

  @Test
  public void blockTargetIsCancellableAndExclusive() {
    Player player = proxy(Player.class);
    Block block = proxy(Block.class);
    HoloUiContainerPreviewAccessEvent event = new HoloUiContainerPreviewAccessEvent(player, block);

    assertSame(player, event.getPlayer());
    assertSame(block, event.getBlock());
    assertNull(event.getEntity());
    assertFalse(event.isCancelled());
    event.setCancelled(true);
    assertTrue(event.isCancelled());
    assertSame(HoloUiContainerPreviewAccessEvent.getHandlerList(), event.getHandlers());
  }

  @Test
  public void entityTargetIsCancellableAndExclusive() {
    Player player = proxy(Player.class);
    Entity entity = proxy(Entity.class);
    HoloUiContainerPreviewAccessEvent event = new HoloUiContainerPreviewAccessEvent(player, entity);

    assertSame(player, event.getPlayer());
    assertNull(event.getBlock());
    assertSame(entity, event.getEntity());
    assertThrows(NullPointerException.class,
        () -> new HoloUiContainerPreviewAccessEvent(null, entity));
    assertThrows(NullPointerException.class,
        () -> new HoloUiContainerPreviewAccessEvent(player, (Block) null));
  }

  private static <T> T proxy(Class<T> type) {
    return type.cast(Proxy.newProxyInstance(
        HoloUiContainerPreviewAccessEventTest.class.getClassLoader(),
        new Class<?>[]{type},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == arguments[0];
          case "toString" -> type.getSimpleName();
          default -> throw new UnsupportedOperationException(method.getName());
        }
    ));
  }
}
