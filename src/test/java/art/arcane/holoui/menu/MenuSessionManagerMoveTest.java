package art.arcane.holoui.menu;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MenuSessionManagerMoveTest {

  @Test
  public void lockedMovementIsFrozenBeforeDistanceValidation() {
    Location from = new Location(null, 2D, 64D, 3D);
    Location to = new Location(null, 200D, 120D, -300D);
    AtomicReference<Vector> velocity = new AtomicReference<>(new Vector(1D, 2D, 3D));
    MenuDefinitionData data = new MenuDefinitionData(new Vector(), true, false, 1D, false, false,
        List.<MenuComponentData>of());
    data.setId("locked");
    MenuSession session = new MenuSession(data, player(from, velocity));

    HoloCloseReason reason = MenuSessionManager.handleMovement(session, from, to);

    assertNull(reason);
    assertEquals(from.getX(), to.getX(), 0D);
    assertEquals(from.getY(), to.getY(), 0D);
    assertEquals(from.getZ(), to.getZ(), 0D);
    assertEquals(new Vector(), velocity.get());
  }

  private static Player player(Location location, AtomicReference<Vector> velocity) {
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getLocation" -> location.clone();
          case "getVelocity" -> velocity.get();
          case "setVelocity" -> {
            velocity.set((Vector) args[0]);
            yield null;
          }
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Player[locked]";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
