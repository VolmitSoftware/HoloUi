package art.arcane.holoui.menu.special.inventories;

import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.EnderChest;
import org.bukkit.block.Lockable;
import org.bukkit.permissions.Permissible;
import org.bukkit.util.BoundingBox;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContainerPreviewAccessTest {

  @Test
  public void canonicalPermissionAllowsViewer() {
    AtomicReference<String> checkedPermission = new AtomicReference<>();
    Permissible viewer = permissible(true, checkedPermission, new AtomicInteger());

    assertTrue(ContainerPreviewAccess.canView(viewer, true));
    assertEquals(ContainerPreviewAccess.PERMISSION, checkedPermission.get());
  }

  @Test
  public void disabledPreviewSkipsPermissionLookup() {
    AtomicInteger permissionChecks = new AtomicInteger();
    Permissible viewer = permissible(true, new AtomicReference<>(), permissionChecks);

    assertFalse(ContainerPreviewAccess.canView(viewer, false));
    assertEquals(0, permissionChecks.get());
  }

  @Test
  public void missingPermissionDeniesViewer() {
    Permissible viewer = permissible(false, new AtomicReference<>(), new AtomicInteger());

    assertFalse(ContainerPreviewAccess.canView(viewer, true));
  }

  @Test
  public void blockedChestIsNotOpenable() {
    assertFalse(ContainerPreviewAccess.isOpenable(blockState(Chest.class, true, false)));
    assertTrue(ContainerPreviewAccess.isOpenable(blockState(Chest.class, false, false)));
  }

  @Test
  public void blockedEnderChestIsNotOpenable() {
    assertFalse(ContainerPreviewAccess.isOpenable(blockState(EnderChest.class, true, false)));
    assertTrue(ContainerPreviewAccess.isOpenable(blockState(EnderChest.class, false, false)));
  }

  @Test
  public void lockedContainerIsNotOpenable() {
    BlockState lockedState = (BlockState) Proxy.newProxyInstance(
        ContainerPreviewAccessTest.class.getClassLoader(),
        new Class<?>[]{BlockState.class, Lockable.class},
        (proxy, method, arguments) -> invokeBlockState(proxy, method, arguments, false, true)
    );

    assertFalse(ContainerPreviewAccess.isOpenable(lockedState));
  }

  @Test
  public void shulkerLidBoundsCoverEveryFacing() {
    assertBounds(BlockFace.DOWN, 10.0D, 19.5D, 30.0D, 11.0D, 20.0D, 31.0D);
    assertBounds(BlockFace.UP, 10.0D, 21.0D, 30.0D, 11.0D, 21.5D, 31.0D);
    assertBounds(BlockFace.NORTH, 10.0D, 20.0D, 29.5D, 11.0D, 21.0D, 30.0D);
    assertBounds(BlockFace.SOUTH, 10.0D, 20.0D, 31.0D, 11.0D, 21.0D, 31.5D);
    assertBounds(BlockFace.WEST, 9.5D, 20.0D, 30.0D, 10.0D, 21.0D, 31.0D);
    assertBounds(BlockFace.EAST, 11.0D, 20.0D, 30.0D, 11.5D, 21.0D, 31.0D);
  }

  private static Permissible permissible(boolean allowed, AtomicReference<String> checkedPermission, AtomicInteger permissionChecks) {
    return (Permissible) Proxy.newProxyInstance(
        ContainerPreviewAccessTest.class.getClassLoader(),
        new Class<?>[]{Permissible.class},
        (proxy, method, arguments) -> {
          if (method.getName().equals("hasPermission") && arguments[0] instanceof String permission) {
            checkedPermission.set(permission);
            permissionChecks.incrementAndGet();
            return allowed;
          }
          return invokeIdentity(proxy, method, arguments);
        }
    );
  }

  private static <T extends BlockState> BlockState blockState(Class<T> type, boolean blocked, boolean locked) {
    return (BlockState) Proxy.newProxyInstance(
        ContainerPreviewAccessTest.class.getClassLoader(),
        new Class<?>[]{type},
        (proxy, method, arguments) -> invokeBlockState(proxy, method, arguments, blocked, locked)
    );
  }

  private static Object invokeBlockState(Object proxy, Method method, Object[] arguments, boolean blocked, boolean locked) {
    return switch (method.getName()) {
      case "isBlocked" -> blocked;
      case "isLocked" -> locked;
      default -> invokeIdentity(proxy, method, arguments);
    };
  }

  private static Object invokeIdentity(Object proxy, Method method, Object[] arguments) {
    return switch (method.getName()) {
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == arguments[0];
      case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName();
      default -> throw new UnsupportedOperationException(method.getName());
    };
  }

  private static void assertBounds(BlockFace face, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    BoundingBox bounds = ContainerPreviewAccess.shulkerLidBounds(10, 20, 30, face);
    double epsilon = 2.0E-6D;
    assertEquals(minX, bounds.getMinX(), epsilon);
    assertEquals(minY, bounds.getMinY(), epsilon);
    assertEquals(minZ, bounds.getMinZ(), epsilon);
    assertEquals(maxX, bounds.getMaxX(), epsilon);
    assertEquals(maxY, bounds.getMaxY(), epsilon);
    assertEquals(maxZ, bounds.getMaxZ(), epsilon);
  }
}
