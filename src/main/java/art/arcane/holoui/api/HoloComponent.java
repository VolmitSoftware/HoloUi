package art.arcane.holoui.api;

import java.util.Objects;

public sealed interface HoloComponent permits HoloComponent.Decoration, HoloComponent.Button {

  float DEFAULT_HIGHLIGHT_MODIFIER = 0.05F;

  String id();

  double offsetX();

  double offsetY();

  double offsetZ();

  HoloIcon icon();

  record Decoration(String id, double offsetX, double offsetY, double offsetZ, HoloIcon icon)
      implements HoloComponent {
    public Decoration {
      id = HoloText.sanitizeId(id);
      Objects.requireNonNull(icon, "icon");
    }
  }

  record Button(String id, double offsetX, double offsetY, double offsetZ, HoloIcon icon,
                float highlightModifier, HoloClickHandler handler) implements HoloComponent {
    public Button {
      id = HoloText.sanitizeId(id);
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(handler, "handler");
      highlightModifier = Float.isFinite(highlightModifier) ? Math.max(0.0F, Math.min(1.0F, highlightModifier)) : 0.0F;
    }
  }

  static HoloComponent decoration(String id, double x, double y, double z, HoloIcon icon) {
    return new Decoration(id, x, y, z, icon);
  }

  static HoloComponent button(String id, double x, double y, double z, HoloIcon icon, HoloClickHandler handler) {
    return new Button(id, x, y, z, icon, DEFAULT_HIGHLIGHT_MODIFIER, handler);
  }
}
