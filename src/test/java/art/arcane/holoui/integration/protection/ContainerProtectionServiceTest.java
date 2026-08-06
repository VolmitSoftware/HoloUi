package art.arcane.holoui.integration.protection;

import art.arcane.holoui.api.HoloUiContainerPreviewAccessEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContainerProtectionServiceTest {

  @Test
  public void providerAndAccessEventMustBothAllowBlock() {
    Player player = proxy(Player.class);
    Block block = proxy(Block.class);
    AtomicInteger events = new AtomicInteger();
    ContainerProtectionService service = service(provider(true, true), event -> events.incrementAndGet());

    assertTrue(service.canAccess(player, block));
    assertEquals(1, events.get());

    ContainerProtectionService cancelled = service(provider(true, true), event -> {
      events.incrementAndGet();
      ((HoloUiContainerPreviewAccessEvent) event).setCancelled(true);
    });
    assertFalse(cancelled.canAccess(player, block));
  }

  @Test
  public void providerDenialSkipsAccessEvent() {
    AtomicInteger events = new AtomicInteger();
    ContainerProtectionService service = service(provider(false, false), event -> events.incrementAndGet());

    assertFalse(service.canAccess(proxy(Player.class), proxy(Block.class)));
    assertFalse(service.canAccess(proxy(Player.class), proxy(Entity.class)));
    assertEquals(0, events.get());
  }

  @Test
  public void providerFailureLocksPreview() {
    ContainerProtectionProvider failing = new ContainerProtectionProvider() {
      @Override
      public boolean canAccess(Player player, Block block) throws ReflectiveOperationException {
        throw new ReflectiveOperationException("block failure");
      }

      @Override
      public boolean canAccess(Player player, Entity entity) throws ReflectiveOperationException {
        throw new ReflectiveOperationException("entity failure");
      }
    };
    ContainerProtectionService service = service(failing, event -> {
    });

    assertFalse(service.canAccess(proxy(Player.class), proxy(Block.class)));
    assertFalse(service.canAccess(proxy(Player.class), proxy(Entity.class)));
  }

  private static ContainerProtectionService service(ContainerProtectionProvider provider,
                                                     Consumer<Event> dispatcher) {
    return new ContainerProtectionService(plugin(), provider, dispatcher);
  }

  private static ContainerProtectionProvider provider(boolean blocks, boolean entities) {
    return new ContainerProtectionProvider() {
      @Override
      public boolean canAccess(Player player, Block block) {
        return blocks;
      }

      @Override
      public boolean canAccess(Player player, Entity entity) {
        return entities;
      }
    };
  }

  private static Plugin plugin() {
    Logger logger = Logger.getLogger(ContainerProtectionServiceTest.class.getName());
    return (Plugin) Proxy.newProxyInstance(
        ContainerProtectionServiceTest.class.getClassLoader(),
        new Class<?>[]{Plugin.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "getLogger" -> logger;
          case "getName" -> "HoloUi";
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == arguments[0];
          case "toString" -> "HoloUi";
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  private static <T> T proxy(Class<T> type) {
    return type.cast(Proxy.newProxyInstance(
        ContainerProtectionServiceTest.class.getClassLoader(),
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
