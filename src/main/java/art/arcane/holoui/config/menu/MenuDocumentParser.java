package art.arcane.holoui.config.menu;

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.action.MenuActionData;
import art.arcane.holoui.config.components.ButtonComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.config.components.ToggleComponentData;
import art.arcane.holoui.menu.action.MenuAction;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.StringReader;
import java.util.List;

public final class MenuDocumentParser {
  private MenuDocumentParser() {
  }

  public static MenuDocument parse(String menuId, String source) {
    String requiredId = MenuIds.require(menuId);
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("menu document must not be empty");
    }
    JsonElement root = JsonParser.parseString(source);
    if (!root.isJsonObject()) {
      throw new IllegalArgumentException("menu document must be a JSON object");
    }
    MenuDefinitionData definition = BukkitJson.parse(new StringReader(source), MenuDefinitionData.class);
    if (definition == null) {
      throw new IllegalArgumentException("menu document must not be null");
    }
    definition.setId(requiredId);
    precompileActions(definition);
    return new MenuDocument(requiredId, MenuDocument.revisionOf(source), source, definition);
  }

  private static void precompileActions(MenuDefinitionData menu) {
    if (menu.getComponents() == null) {
      return;
    }
    for (MenuComponentData component : menu.getComponents()) {
      if (component == null || component.data() == null) {
        continue;
      }
      ComponentData data = component.data();
      if (data instanceof ButtonComponentData button) {
        resolveActions(button.actions(), menu.getId(), component.id());
      } else if (data instanceof ToggleComponentData toggle) {
        resolveActions(toggle.trueActions(), menu.getId(), component.id());
        resolveActions(toggle.falseActions(), menu.getId(), component.id());
      }
    }
  }

  private static void resolveActions(List<MenuActionData> actions, String menuId, String componentId) {
    MenuAction.resolve(actions, menuId, componentId);
  }
}
