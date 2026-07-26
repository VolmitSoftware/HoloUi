package art.arcane.holoui.api.internal;

import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public record ApiOwner(String name, BooleanSupplier enabled) {
  public ApiOwner {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(enabled, "enabled");
  }

  public static ApiOwner of(Plugin plugin) {
    Objects.requireNonNull(plugin, "owner");
    return new ApiOwner(plugin.getName(), plugin::isEnabled);
  }

  public boolean active() {
    return enabled.getAsBoolean();
  }
}
