package art.arcane.holoui.importer;

import org.bukkit.World;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class LegacyWorldCatalog {
  private final Map<String, WorldDescriptor> byReference;

  private LegacyWorldCatalog(Map<String, WorldDescriptor> byReference) {
    this.byReference = Map.copyOf(byReference);
  }

  static LegacyWorldCatalog capture(Collection<World> worlds) {
    Map<String, WorldDescriptor> references = new LinkedHashMap<>();
    for (World world : Objects.requireNonNull(worlds, "worlds")) {
      WorldDescriptor descriptor = new WorldDescriptor(world.getName(), world.getKey().toString(),
          world.getUID());
      references.putIfAbsent(normalize(descriptor.name()), descriptor);
      references.putIfAbsent(normalize(descriptor.key()), descriptor);
      references.putIfAbsent(normalize(descriptor.uuid().toString()), descriptor);
    }
    return new LegacyWorldCatalog(references);
  }

  static LegacyWorldCatalog of(WorldDescriptor... worlds) {
    Map<String, WorldDescriptor> references = new LinkedHashMap<>();
    for (WorldDescriptor descriptor : worlds) {
      references.putIfAbsent(normalize(descriptor.name()), descriptor);
      references.putIfAbsent(normalize(descriptor.key()), descriptor);
      references.putIfAbsent(normalize(descriptor.uuid().toString()), descriptor);
    }
    return new LegacyWorldCatalog(references);
  }

  Optional<WorldDescriptor> resolve(String reference) {
    if (reference == null || reference.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byReference.get(normalize(reference)));
  }

  private static String normalize(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }

  record WorldDescriptor(String name, String key, UUID uuid) {
    WorldDescriptor {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("world name must not be blank");
      }
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("world key must not be blank");
      }
      uuid = Objects.requireNonNull(uuid, "uuid");
    }
  }
}
