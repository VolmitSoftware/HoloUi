/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of third-party {@link PreviewStateProvider}s. Registration is process-wide and lives
 * for the lifetime of the JVM, so plugins should unregister on disable.
 *
 * <p>Backed by a copy-on-write list: registration is rare, iteration happens on region threads
 * during every preview snapshot.
 */
public final class PreviewStateProviders {

  private static final CopyOnWriteArrayList<PreviewStateProvider> PROVIDERS = new CopyOnWriteArrayList<>();

  private PreviewStateProviders() {
  }

  /** Registers a provider. Registering the same instance twice is a no-op. */
  public static void register(PreviewStateProvider provider) {
    Objects.requireNonNull(provider, "provider");
    PROVIDERS.addIfAbsent(provider);
  }

  /** Removes a previously registered provider. Unknown instances are ignored. */
  public static void unregister(PreviewStateProvider provider) {
    if (provider != null) {
      PROVIDERS.remove(provider);
    }
  }

  /** Every registered provider, in registration order. */
  public static List<PreviewStateProvider> all() {
    return Collections.unmodifiableList(PROVIDERS);
  }
}
