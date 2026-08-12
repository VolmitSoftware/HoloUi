package art.arcane.holoui.menu;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.enums.MenuComponentType;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.icon.MenuIcon;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MenuSessionComponentDeduplicationTest {

  @Test
  public void duplicateComponentIdsKeepOnlyTheFirstComponent() {
    MenuDefinitionData menu = new MenuDefinitionData(new Vector(), false, false, 8D, false, false, List.of(
        component("duplicate", "first"),
        component("duplicate", "second"),
        component("unique", "third")
    ));
    menu.setId("test");

    Player player = player();
    MenuSession session = new MenuSession(menu, player, MenuSessionOptions.personal(menu, player, null));

    assertEquals(List.of("first", "third"), session.getComponents().stream()
        .map(component -> ((ProbeComponent) component).marker)
        .toList());
  }

  private static MenuComponentData component(String id, String marker) {
    return new MenuComponentData(id, new Vector(), new ProbeData(marker));
  }

  private static Player player() {
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getLocation" -> new Location(null, 0D, 64D, 0D);
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Player[test]";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  private record ProbeData(String marker) implements ComponentData {
    @Override
    public MenuComponentType getType() {
      return MenuComponentType.DECO;
    }

    @Override
    public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
      return new ProbeComponent(session, data, marker);
    }
  }

  private static final class ProbeComponent extends MenuComponent<ProbeData> {
    private final String marker;

    private ProbeComponent(MenuSession session, MenuComponentData data, String marker) {
      super(session, data);
      this.marker = marker;
    }

    @Override
    protected void onTick() {
    }

    @Override
    protected MenuIcon<?> createIcon() {
      return null;
    }

    @Override
    protected void onOpen() {
    }

    @Override
    protected void onClose() {
    }
  }
}
