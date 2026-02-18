package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.icon.TextMenuIcon;
import org.bukkit.Location;

public class MapPanelIcon extends TextMenuIcon {

  private final float panelScale;

  public MapPanelIcon(MenuSession session, Location loc, MapPanelComponent.Data data) throws MenuIconException {
    super(session, loc, new TextIconData(data.text()));
    this.panelScale = data.scale();
  }

  @Override
  protected float uiScale() {
    return HuiSettings.uiScale()
        * HuiSettings.previewTextScale()
        * HuiSettings.previewPanelScale()
        * panelScale;
  }
}
