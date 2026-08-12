package art.arcane.holoui.board;

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.MenuTransform;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class BoardPlacement {
  private BoardPlacement() {
  }

  public static World resolveWorld(BoardDefinition board) {
    BoardTransform transform = Objects.requireNonNull(board, "board").transform();
    World world = Bukkit.getWorld(transform.worldUuid());
    if (world == null || !world.getKey().toString().equals(transform.worldKey())) {
      return null;
    }
    return world;
  }

  public static MenuTransform menuTransform(BoardDefinition board, MenuDefinitionData menu) {
    return menuTransform(board.transform(), menu);
  }

  public static MenuTransform menuTransform(BoardTransform transform, MenuDefinitionData menu) {
    World world = Bukkit.getWorld(Objects.requireNonNull(transform, "transform").worldUuid());
    if (world == null || !world.getKey().toString().equals(transform.worldKey())) {
      return null;
    }
    Location anchor = new Location(
        world,
        transform.x(),
        transform.y(),
        transform.z(),
        (float) transform.yaw(),
        (float) transform.pitch()
    );
    return new MenuTransform(
        anchor,
        Objects.requireNonNull(menu, "menu").getOffset(),
        (float) transform.yaw(),
        (float) transform.pitch(),
        (float) transform.roll(),
        (float) (transform.scale() * HuiSettings.uiScale())
    );
  }

  public static double distanceSquared(BoardDefinition board, Location location) {
    return distanceSquared(Objects.requireNonNull(board, "board").transform(), location);
  }

  public static double distanceSquared(BoardTransform transform, Location location) {
    BoardTransform requiredTransform = Objects.requireNonNull(transform, "transform");
    Location point = Objects.requireNonNull(location, "location");
    if (point.getWorld() == null || !point.getWorld().getUID().equals(requiredTransform.worldUuid())) {
      return Double.POSITIVE_INFINITY;
    }
    double x = point.getX() - requiredTransform.x();
    double y = point.getY() - requiredTransform.y();
    double z = point.getZ() - requiredTransform.z();
    return x * x + y * y + z * z;
  }
}
