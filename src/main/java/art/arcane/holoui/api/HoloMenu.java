package art.arcane.holoui.api;

import java.util.List;
import java.util.Objects;

public record HoloMenu(String id, double offsetX, double offsetY, double offsetZ, boolean lockPosition,
                       boolean followPlayer, double maxDistance, boolean closeOnDeath, boolean closeOnTeleport,
                       List<HoloComponent> components) {
  public HoloMenu {
    id = HoloText.sanitizeId(id);
    Objects.requireNonNull(components, "components");
    components = List.copyOf(components);
    HoloText.requireDistinctIds(components);
  }

  public static HoloMenuBuilder builder() {
    return new HoloMenuBuilder();
  }
}
