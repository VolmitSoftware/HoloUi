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
package art.arcane.holoui.integration;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.HuiSettings;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class ItemProviderRegistry implements Listener {

  public static final String AUTO_PROVIDER = "auto";

  private static final String PROVIDER_PACKAGE = "art.arcane.holoui.integration.provider.";
  private static final List<ProviderDefinition> BUILT_IN_PROVIDERS = List.of(
      new ProviderDefinition("CraftEngine", PROVIDER_PACKAGE + "CraftEngineItemProvider"),
      new ProviderDefinition("ItemsAdder", PROVIDER_PACKAGE + "ItemsAdderItemProvider"),
      new ProviderDefinition("Oraxen", PROVIDER_PACKAGE + "OraxenItemProvider"),
      new ProviderDefinition("Nexo", PROVIDER_PACKAGE + "NexoItemProvider"),
      new ProviderDefinition("MMOItems", PROVIDER_PACKAGE + "MMOItemsItemProvider"),
      new ProviderDefinition("ExecutableItems", PROVIDER_PACKAGE + "ExecutableItemsItemProvider"),
      new ProviderDefinition("EcoItems", PROVIDER_PACKAGE + "EcoItemsItemProvider"),
      new ProviderDefinition("Slimefun", PROVIDER_PACKAGE + "SlimefunItemProvider"),
      new ProviderDefinition("MythicMobs", PROVIDER_PACKAGE + "MythicMobsItemProvider"),
      new ProviderDefinition("HeadDatabase", PROVIDER_PACKAGE + "HeadDatabaseItemProvider")
  );

  private final Plugin plugin;
  private final Object lock = new Object();
  private final List<ItemProvider> providers = new ArrayList<>();
  private final Map<String, ItemStack> prototypes = new ConcurrentHashMap<>();
  private final Set<String> offThreadWarned = ConcurrentHashMap.newKeySet();
  private final Set<String> faultWarned = ConcurrentHashMap.newKeySet();

  private volatile List<ItemProvider> snapshot = List.of();
  private volatile boolean listening;

  public ItemProviderRegistry(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public static List<ProviderDefinition> definitions() {
    return BUILT_IN_PROVIDERS;
  }

  public void activateAll() {
    if (!listening) {
      Bukkit.getPluginManager().registerEvents(this, plugin);
      listening = true;
    }
    if (!HuiSettings.customItemsEnabled()) {
      return;
    }
    for (ProviderDefinition definition : BUILT_IN_PROVIDERS) {
      activate(definition);
    }
  }

  public void reload() {
    clearProviders();
    activateAll();
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    listening = false;
    clearProviders();
    offThreadWarned.clear();
    faultWarned.clear();
  }

  public void invalidate() {
    prototypes.clear();
  }

  public Collection<ItemProvider> active() {
    return snapshot;
  }

  public Optional<ItemProvider> byId(String providerId) {
    String normalized = normalize(providerId);
    return snapshot.stream()
        .filter(provider -> provider.id().equalsIgnoreCase(normalized))
        .findFirst();
  }

  /**
   * Resolves an item id to a stack the caller owns, or null when nothing matched. A null, blank or
   * {@code auto} provider id tries every ready provider in registration order.
   */
  public ItemStack resolve(String providerId, String itemId) {
    if (itemId == null || itemId.isBlank() || !HuiSettings.customItemsEnabled()) {
      return null;
    }

    String provider = normalize(providerId);
    String key = provider + " " + itemId;
    ItemStack cached = prototypes.get(key);
    if (cached != null) {
      return cached.clone();
    }

    ItemStack resolved = resolveUncached(provider, itemId);
    if (resolved == null) {
      return null;
    }
    // ItemsAdder and Slimefun hand back live registry instances, so neither the cache nor the caller keeps one
    ItemStack prototype = resolved.clone();
    prototypes.put(key, prototype);
    return prototype.clone();
  }

  public List<ProviderStatus> providerStatuses() {
    List<ProviderStatus> statuses = new ArrayList<>(BUILT_IN_PROVIDERS.size());
    for (ProviderDefinition definition : BUILT_IN_PROVIDERS) {
      Plugin host = Bukkit.getPluginManager().getPlugin(definition.pluginName());
      ItemProvider provider = byPluginName(definition.pluginName());
      boolean ready = provider != null && readySafely(provider);
      // every built-in provider id is its plugin name lowercased, so an inactive row still reports the id
      String id = provider == null ? definition.pluginName().toLowerCase(Locale.ROOT) : provider.id();
      statuses.add(new ProviderStatus(id, definition.pluginName(), host != null && host.isEnabled(),
          provider != null, ready, ready ? countIds(provider) : 0));
    }
    return List.copyOf(statuses);
  }

  public String statusSummary() {
    if (!HuiSettings.customItemsEnabled()) {
      return "custom items disabled";
    }
    List<ItemProvider> current = snapshot;
    if (current.isEmpty()) {
      return "0/" + BUILT_IN_PROVIDERS.size() + " providers active";
    }
    return current.size() + "/" + BUILT_IN_PROVIDERS.size() + " providers active: "
        + current.stream().map(ItemProvider::id).collect(Collectors.joining(", "));
  }

  @EventHandler
  public void onPluginEnable(PluginEnableEvent event) {
    if (!HuiSettings.customItemsEnabled()) {
      return;
    }
    String pluginName = event.getPlugin().getName();
    BUILT_IN_PROVIDERS.stream()
        .filter(definition -> definition.pluginName().equalsIgnoreCase(pluginName))
        .findFirst()
        .ifPresent(this::activate);
  }

  @EventHandler
  public void onPluginDisable(PluginDisableEvent event) {
    String pluginName = event.getPlugin().getName();
    boolean removed;
    synchronized (lock) {
      removed = providers.removeIf(provider -> provider.pluginName().equalsIgnoreCase(pluginName));
      if (removed) {
        snapshot = List.copyOf(providers);
      }
    }
    if (removed) {
      prototypes.clear();
      HoloUI.log(Level.INFO, "[items] provider for %s dropped, it was disabled", pluginName);
    }
  }

  private void activate(ProviderDefinition definition) {
    Plugin host = Bukkit.getPluginManager().getPlugin(definition.pluginName());
    if (host == null || !host.isEnabled() || byPluginName(definition.pluginName()) != null) {
      return;
    }

    try {
      ItemProvider provider = Class.forName(definition.className(), true, ItemProviderRegistry.class.getClassLoader())
          .asSubclass(ItemProvider.class)
          .getDeclaredConstructor()
          .newInstance();
      if (!isAllowed(provider) || !register(provider)) {
        return;
      }
      HoloUI.log(Level.INFO, "[items] provider %s active from %s", provider.id(), definition.pluginName());
    } catch (Throwable failure) {
      HoloUI.logExceptionStack(false, failure, "[items] failed to activate the %s item provider:", definition.pluginName());
    }
  }

  private boolean register(ItemProvider provider) {
    synchronized (lock) {
      for (ItemProvider existing : providers) {
        if (existing.pluginName().equalsIgnoreCase(provider.pluginName())) {
          return false;
        }
      }
      providers.add(provider);
      snapshot = List.copyOf(providers);
    }
    prototypes.clear();
    return true;
  }

  private void clearProviders() {
    synchronized (lock) {
      providers.clear();
      snapshot = List.of();
    }
    prototypes.clear();
  }

  private boolean isAllowed(ItemProvider provider) {
    Set<String> allowed = HuiSettings.customItemProviders();
    return allowed.isEmpty()
        || allowed.contains(provider.id().toLowerCase(Locale.ROOT))
        || allowed.contains(provider.pluginName().toLowerCase(Locale.ROOT));
  }

  private ItemStack resolveUncached(String providerId, String itemId) {
    if (AUTO_PROVIDER.equals(providerId)) {
      for (ItemProvider provider : snapshot) {
        ItemStack resolved = resolveFrom(provider, itemId);
        if (resolved != null) {
          return resolved;
        }
      }
      return null;
    }

    ItemProvider provider = byId(providerId).orElse(null);
    return provider == null ? null : resolveFrom(provider, itemId);
  }

  private ItemStack resolveFrom(ItemProvider provider, String itemId) {
    // an off-thread caller must not block on a main-thread-only API, it just misses this lookup
    if (provider.requiresMainThread() && !Bukkit.isPrimaryThread()) {
      if (offThreadWarned.add(provider.id())) {
        HoloUI.log(Level.WARNING, "[items] provider %s is main thread only and was skipped off thread", provider.id());
      }
      return null;
    }
    if (!readySafely(provider)) {
      return null;
    }

    try {
      return provider.resolve(itemId);
    } catch (Throwable failure) {
      warnFault(provider, failure);
      return null;
    }
  }

  private boolean readySafely(ItemProvider provider) {
    try {
      return provider.isReady();
    } catch (Throwable failure) {
      warnFault(provider, failure);
      return false;
    }
  }

  private int countIds(ItemProvider provider) {
    try {
      Collection<String> ids = provider.listIds();
      return ids == null ? 0 : ids.size();
    } catch (Throwable failure) {
      warnFault(provider, failure);
      return 0;
    }
  }

  private void warnFault(ItemProvider provider, Throwable failure) {
    if (!faultWarned.add(provider.id())) {
      return;
    }
    HoloUI.logExceptionStack(false, failure, "[items] provider %s faulted, lookups against it will keep failing:", provider.id());
  }

  private ItemProvider byPluginName(String pluginName) {
    for (ItemProvider provider : snapshot) {
      if (provider.pluginName().equalsIgnoreCase(pluginName)) {
        return provider;
      }
    }
    return null;
  }

  private static String normalize(String providerId) {
    return providerId == null || providerId.isBlank() ? AUTO_PROVIDER : providerId.trim().toLowerCase(Locale.ROOT);
  }
}
