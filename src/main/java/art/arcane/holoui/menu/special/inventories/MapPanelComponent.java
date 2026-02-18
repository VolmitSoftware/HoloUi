package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.enums.MenuComponentType;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.holoui.menu.icon.MenuIcon;

public class MapPanelComponent extends MenuComponent<MapPanelComponent.Data> {

  public MapPanelComponent(MenuSession session, MenuComponentData data) {
    super(session, data);
  }

  @Override
  protected void onTick() {
  }

  @Override
  protected MenuIcon<?> createIcon() {
    try {
      return new MapPanelIcon(session, getLocation(), data);
    } catch (MenuIconException e) {
      return MenuIcon.createIcon(session, getLocation(), new TextIconData("&8[ ]"), this);
    }
  }

  @Override
  protected void onOpen() {
  }

  @Override
  protected void onClose() {
  }

  public record Data(String text, float scale) implements ComponentData {
    @Override
    public MenuComponentType getType() {
      return null;
    }

    @Override
    public MenuComponent<?> createComponent(MenuSession session, MenuComponentData data) {
      return new MapPanelComponent(session, data);
    }
  }
}
