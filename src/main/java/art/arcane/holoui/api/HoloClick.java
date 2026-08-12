package art.arcane.holoui.api;

import org.bukkit.entity.Player;

import java.util.Objects;

public record HoloClick(Player player, String menuId, String componentId,
                        HoloClickTrigger trigger, HoloMenuHandle handle) {
  public HoloClick {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(trigger, "trigger");
    Objects.requireNonNull(handle, "handle");
  }
}
