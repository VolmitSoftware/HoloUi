package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloComponent;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenu;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.components.ButtonComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.config.components.DecoComponentData;
import art.arcane.holoui.config.icon.AnimatedImageData;
import art.arcane.holoui.config.icon.ItemStackIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.config.icon.TextImageIconData;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ApiMenuTranslator {

  private ApiMenuTranslator() {
  }

  public static MenuDefinitionData definition(HoloMenu menu) {
    List<MenuComponentData> components = new ArrayList<>(menu.components().size());

    for (HoloComponent component : menu.components()) {
      components.add(new MenuComponentData(component.id(),
          new Vector(component.offsetX(), component.offsetY(), component.offsetZ()),
          componentData(component)));
    }

    MenuDefinitionData definition = new MenuDefinitionData(
        new Vector(menu.offsetX(), menu.offsetY(), menu.offsetZ()),
        menu.lockPosition(),
        menu.followPlayer(),
        menu.maxDistance(),
        menu.closeOnDeath(),
        menu.closeOnTeleport(),
        List.copyOf(components));
    definition.setId(menu.id());
    return definition;
  }

  public static Map<String, HoloClickHandler> handlers(HoloMenu menu) {
    Map<String, HoloClickHandler> handlers = new HashMap<>();

    for (HoloComponent component : menu.components()) {
      if (component instanceof HoloComponent.Button button) {
        handlers.put(button.id(), button.handler());
      }
    }

    return Map.copyOf(handlers);
  }

  public static MenuIconData iconData(HoloIcon icon) {
    return switch (icon) {
      case HoloIcon.Text text -> new TextIconData(text.miniMessage());
      case HoloIcon.Item item -> new ItemStackIconData(item.stack());
      case HoloIcon.Image image -> new TextImageIconData(image.relativePath());
      case HoloIcon.AnimatedImage animated -> new AnimatedImageData(animated.relativePaths(), animated.tickSpeed());
    };
  }

  private static ComponentData componentData(HoloComponent component) {
    return switch (component) {
      case HoloComponent.Decoration decoration -> new DecoComponentData(iconData(decoration.icon()));
      case HoloComponent.Button button ->
          new ButtonComponentData(button.highlightModifier(), List.of(), iconData(button.icon()), null);
    };
  }
}
