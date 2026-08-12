package art.arcane.holoui.menu;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MenuSessionManagerMoveTest {

  private static final World WORLD = (World) Proxy.newProxyInstance(
      World.class.getClassLoader(),
      new Class<?>[]{World.class},
      (proxy, method, args) -> switch (method.getName()) {
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        case "toString" -> "World[test]";
        default -> throw new UnsupportedOperationException(method.getName());
      }
  );

  @Test
  public void lockedMovementIsFrozenBeforeDistanceValidation() {
    Location from = new Location(null, 2D, 64D, 3D);
    Location to = new Location(null, 200D, 120D, -300D);
    AtomicReference<Location> playerLocation = new AtomicReference<>(from);
    AtomicReference<Vector> velocity = new AtomicReference<>(new Vector(1D, 2D, 3D));
    MenuDefinitionData data = new MenuDefinitionData(new Vector(), true, false, 1D, false, false,
        List.<MenuComponentData>of());
    data.setId("locked");
    Player player = player(playerLocation, velocity);
    MenuSession session = new MenuSession(data, player, MenuSessionOptions.personal(data, player, null));

    HoloCloseReason reason = MenuSessionManager.handleMovement(session, from, to);

    assertNull(reason);
    assertEquals(from.getX(), to.getX(), 0D);
    assertEquals(from.getY(), to.getY(), 0D);
    assertEquals(from.getZ(), to.getZ(), 0D);
    assertEquals(new Vector(), velocity.get());
  }

  @Test
  public void manualMovePreservesTheOpeningFacing() {
    Location opening = new Location(null, 2D, 64D, 3D, 90F, 0F);
    AtomicReference<Location> playerLocation = new AtomicReference<>(opening);
    AtomicReference<Vector> velocity = new AtomicReference<>(new Vector());
    MenuDefinitionData data = new MenuDefinitionData(new Vector(1D, 2D, 3D), false, false, 8D, false, false,
        List.<MenuComponentData>of());
    data.setId("movable");
    Player player = player(playerLocation, velocity);
    MenuSession session = new MenuSession(data, player, MenuSessionOptions.personal(data, player, null));
    session.open();
    float openingYaw = session.getTransform().facingYaw();
    Location anchor = new Location(null, 20D, 80D, 30D, -45F, 0F);
    playerLocation.set(anchor);

    session.move(anchor);

    assertEquals(90F, openingYaw, 0F);
    assertEquals(openingYaw, session.getTransform().facingYaw(), 0F);
    assertEquals(270F, session.getTransform().displayYaw(), 0F);
    assertEquals(17D, session.getCenterPoint().getX(), 0.000001D);
    assertEquals(82D, session.getCenterPoint().getY(), 0D);
    assertEquals(29D, session.getCenterPoint().getZ(), 0.000001D);
    assertEquals(20D, anchor.getX(), 0D);
    assertEquals(80D, anchor.getY(), 0D);
    assertEquals(30D, anchor.getZ(), 0D);
  }

  @Test
  public void followMovementTracksYawChangesWithoutTranslation() {
    Location opening = new Location(WORLD, 2D, 64D, 3D, 0F, 0F);
    AtomicReference<Location> playerLocation = new AtomicReference<>(opening);
    AtomicReference<Vector> velocity = new AtomicReference<>(new Vector());
    MenuDefinitionData data = new MenuDefinitionData(new Vector(0D, 0D, 2D), false, true, 8D, false, false,
        List.<MenuComponentData>of());
    data.setId("following");
    Player player = player(playerLocation, velocity);
    MenuSession session = new MenuSession(data, player, MenuSessionOptions.personal(data, player, null));
    session.open();
    Location turned = opening.clone();
    turned.setYaw(90F);

    HoloCloseReason reason = MenuSessionManager.handleMovement(session, opening, turned);

    assertNull(reason);
    assertEquals(90F, session.getTransform().facingYaw(), 0F);
    assertEquals(270F, session.getTransform().displayYaw(), 0F);
    assertEquals(0D, session.getCenterPoint().getX(), 0.000001D);
    assertEquals(64D, session.getCenterPoint().getY(), 0D);
    assertEquals(3D, session.getCenterPoint().getZ(), 0.000001D);
    assertEquals(2D, turned.getX(), 0D);
    assertEquals(64D, turned.getY(), 0D);
    assertEquals(3D, turned.getZ(), 0D);
  }

  @Test
  public void lockedFollowingMenuStillTurnsWithThePlayer() {
    Location opening = new Location(WORLD, 2D, 64D, 3D, 0F, 0F);
    AtomicReference<Location> playerLocation = new AtomicReference<>(opening);
    AtomicReference<Vector> velocity = new AtomicReference<>(new Vector(1D, 0D, 0D));
    MenuDefinitionData data = new MenuDefinitionData(new Vector(0D, 0D, 2D), true, true, 8D, false, false,
        List.<MenuComponentData>of());
    data.setId("locked-following");
    Player player = player(playerLocation, velocity);
    MenuSession session = new MenuSession(data, player, MenuSessionOptions.personal(data, player, null));
    session.open();
    Location turnedAndMoved = new Location(WORLD, 20D, 80D, 30D, 90F, 0F);

    HoloCloseReason reason = MenuSessionManager.handleMovement(session, opening, turnedAndMoved);

    assertNull(reason);
    assertEquals(opening.getX(), turnedAndMoved.getX(), 0D);
    assertEquals(opening.getY(), turnedAndMoved.getY(), 0D);
    assertEquals(opening.getZ(), turnedAndMoved.getZ(), 0D);
    assertEquals(90F, session.getTransform().facingYaw(), 0F);
    assertEquals(0D, session.getCenterPoint().getX(), 0.000001D);
    assertEquals(64D, session.getCenterPoint().getY(), 0D);
    assertEquals(3D, session.getCenterPoint().getZ(), 0.000001D);
    assertEquals(new Vector(), velocity.get());
  }

  private static Player player(AtomicReference<Location> location, AtomicReference<Vector> velocity) {
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getLocation" -> location.get().clone();
          case "getEyeLocation" -> location.get().clone();
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
