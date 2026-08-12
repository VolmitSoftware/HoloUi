package art.arcane.holoui.board;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class BoardFollowTransformTest {
  private static final UUID WORLD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000301");

  @Test
  public void yawFollowRotatesOffsetAndFacingWithTarget() {
    World world = world();
    BoardTransform absolute = new BoardTransform("example:world", WORLD_UUID,
        -2D, 66D, 10D, 110D, 5D, 7D, 1.5D);
    Location initialTarget = new Location(world, 0D, 64D, 10D, 90F, 0F);
    BoardTransform relative = BoardFollowTransform.relativeTo(absolute, initialTarget, BoardFollowRotation.YAW);
    BoardDefinition board = BoardDefinition.create("moving", "menu", relative)
        .withFollow(BoardFollow.player(UUID.randomUUID(), BoardFollowRotation.YAW));

    BoardTransform initial = BoardFollowTransform.resolve(board, initialTarget);
    BoardTransform turned = BoardFollowTransform.resolve(board, new Location(world, 10D, 70D, 20D, 180F, 0F));

    assertTransform(absolute, initial);
    assertEquals(10D, turned.x(), 0.000001D);
    assertEquals(72D, turned.y(), 0.000001D);
    assertEquals(18D, turned.z(), 0.000001D);
    assertEquals(-160D, turned.yaw(), 0.000001D);
  }

  @Test
  public void fullFollowAddsPitchWhileFixedOnlyTranslates() {
    World world = world();
    Location target = new Location(world, 10D, 20D, 30D, 45F, 30F);
    BoardTransform relative = new BoardTransform("example:world", WORLD_UUID,
        1D, 2D, 3D, 5D, 10D, 15D, 1D);
    UUID playerId = UUID.randomUUID();
    BoardDefinition full = BoardDefinition.create("full", "menu", relative)
        .withFollow(BoardFollow.player(playerId, BoardFollowRotation.FULL));
    BoardDefinition fixed = BoardDefinition.create("fixed", "menu", relative)
        .withFollow(BoardFollow.player(playerId, BoardFollowRotation.FIXED));

    BoardTransform fullResult = BoardFollowTransform.resolve(full, target);
    BoardTransform fixedResult = BoardFollowTransform.resolve(fixed, target);

    assertEquals(50D, fullResult.yaw(), 0.000001D);
    assertEquals(40D, fullResult.pitch(), 0.000001D);
    assertEquals(5D, fixedResult.yaw(), 0.000001D);
    assertEquals(10D, fixedResult.pitch(), 0.000001D);
    assertEquals(11D, fixedResult.x(), 0.000001D);
    assertEquals(22D, fixedResult.y(), 0.000001D);
    assertEquals(33D, fixedResult.z(), 0.000001D);
  }

  @Test
  public void fullFollowRotatesItsOffsetThroughPitchAndRoundTripsTheAttachPose() {
    World world = world();
    Location target = new Location(world, 10D, 20D, 30D, 90F, 30F);
    BoardTransform absolute = new BoardTransform("example:world", WORLD_UUID,
        8D, 19D, 34D, 105D, 40D, 5D, 1D);
    BoardTransform relative = BoardFollowTransform.relativeTo(absolute, target, BoardFollowRotation.FULL);
    BoardDefinition board = BoardDefinition.create("full-round-trip", "menu", relative)
        .withFollow(BoardFollow.player(UUID.randomUUID(), BoardFollowRotation.FULL));

    assertTransform(absolute, BoardFollowTransform.resolve(board, target));

    Location lookingDown = new Location(world, 10D, 20D, 30D, 90F, 90F);
    BoardTransform pitched = BoardFollowTransform.resolve(board, lookingDown);
    assertEquals(9.866025D, pitched.x(), 0.000001D);
    assertEquals(17.767949D, pitched.y(), 0.000001D);
    assertEquals(34D, pitched.z(), 0.000001D);
  }

  @Test
  public void capturedPoseRecomputesReloadedDefinitionWithoutALivePlayerOrWorld() {
    UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000302");
    BoardFollowPose lastPose = new BoardFollowPose(
        "example:world",
        WORLD_UUID,
        10D,
        20D,
        30D,
        90F,
        35F
    );
    BoardDefinition initial = BoardDefinition.create(
        "offline-reload",
        "menu",
        new BoardTransform("example:world", WORLD_UUID, 1D, 2D, 3D, 5D, 10D, 15D, 1D)
    ).withFollow(BoardFollow.player(playerId, BoardFollowRotation.FIXED));

    BoardTransform initialEffective = BoardFollowTransform.resolve(initial, lastPose);
    BoardDefinition reloaded = initial.withTransform(new BoardTransform(
        "example:world",
        WORLD_UUID,
        4D,
        5D,
        6D,
        15D,
        20D,
        25D,
        2D
    )).withRevision(initial.revision() + 1L);
    BoardTransform reloadedEffective = BoardFollowTransform.resolve(reloaded, lastPose);

    assertEquals(11D, initialEffective.x(), 0.000001D);
    assertEquals(22D, initialEffective.y(), 0.000001D);
    assertEquals(33D, initialEffective.z(), 0.000001D);
    assertEquals(14D, reloadedEffective.x(), 0.000001D);
    assertEquals(25D, reloadedEffective.y(), 0.000001D);
    assertEquals(36D, reloadedEffective.z(), 0.000001D);
    assertEquals(15D, reloadedEffective.yaw(), 0.000001D);
    assertEquals(20D, reloadedEffective.pitch(), 0.000001D);
    assertEquals(initial.uuid(), reloaded.uuid());
    assertEquals(initial.revision() + 1L, reloaded.revision());
  }

  private static void assertTransform(BoardTransform expected, BoardTransform actual) {
    assertEquals(expected.worldKey(), actual.worldKey());
    assertEquals(expected.worldUuid(), actual.worldUuid());
    assertEquals(expected.x(), actual.x(), 0.000001D);
    assertEquals(expected.y(), actual.y(), 0.000001D);
    assertEquals(expected.z(), actual.z(), 0.000001D);
    assertEquals(expected.yaw(), actual.yaw(), 0.000001D);
    assertEquals(expected.pitch(), actual.pitch(), 0.000001D);
    assertEquals(expected.roll(), actual.roll(), 0.000001D);
    assertEquals(expected.scale(), actual.scale(), 0.000001D);
  }

  private static World world() {
    return (World) Proxy.newProxyInstance(
        BoardFollowTransformTest.class.getClassLoader(),
        new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getUID" -> WORLD_UUID;
          case "getKey" -> new NamespacedKey("example", "world");
          case "getName" -> "world";
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "world";
          default -> defaultValue(method.getReturnType());
        }
    );
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0F;
    }
    if (type == double.class) {
      return 0D;
    }
    if (type == char.class) {
      return '\0';
    }
    return null;
  }
}
