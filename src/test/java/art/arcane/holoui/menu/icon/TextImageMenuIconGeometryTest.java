package art.arcane.holoui.menu.icon;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuSessionOptions;
import art.arcane.holoui.menu.MenuTransform;
import art.arcane.holoui.menu.action.NavigationResult;
import art.arcane.holoui.util.common.math.CollisionPlane;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TextImageMenuIconGeometryTest {

  @Test
  public void fallbackImageHitboxIncludesTheFullHeightOfEveryRenderedRow() throws Exception {
    MenuTransform transform = new MenuTransform(
        new Location(null, 0D, 0D, 0D),
        new Vector(),
        0F,
        0F,
        0F,
        1F
    );
    MenuDefinitionData definition = new MenuDefinitionData(
        new Vector(),
        false,
        false,
        8D,
        false,
        false,
        List.<MenuComponentData>of()
    );
    definition.setId("image-geometry");
    MenuSession session = new MenuSession(
        definition,
        player(),
        MenuSessionOptions.positioned(transform, request -> NavigationResult.DENIED, 1F)
    );
    TextImageMenuIcon icon = new TextImageMenuIcon(session, new Location(null, 0D, 0D, 3D));

    CollisionPlane plane = icon.createBoundingBox(new Location(null, 0D, 0D, 3D));

    assertEquals(TextImageMenuIcon.MISSING.size() * MenuIcon.NAMETAG_SIZE, plane.getHeight(), 0.000001F);
  }

  private static Player player() {
    return (Player) Proxy.newProxyInstance(
        TextImageMenuIconGeometryTest.class.getClassLoader(),
        new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "image geometry viewer";
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
  }
}
