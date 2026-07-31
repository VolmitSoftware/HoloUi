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
package art.arcane.holoui.integration.catalog;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.integration.ItemProvider;
import art.arcane.holoui.integration.ItemProviderRegistry;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Enumerates every active provider and writes {@code custom-items.json} next to settings.json. Ids
 * are probed by actually resolving them and discarded on failure, so the catalog never advertises an
 * id that would render as the missing icon checker in game.
 */
public final class CustomItemCatalogWriter {

  public static final String FILE_NAME = "custom-items.json";

  // HeadDatabase exposes tens of thousands of ids; a full dump is neither useful to the editor nor cheap to probe
  private static final int MAX_ITEMS_PER_PROVIDER = 10000;
  private static final long MAIN_THREAD_TIMEOUT_SECONDS = 30L;
  // one export at a time process wide, the command and the builder start path each own a writer
  private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
  private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9a-fk-orx]");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Plugin plugin;
  private final ItemProviderRegistry registry;
  private final File file;

  public CustomItemCatalogWriter(Plugin plugin, ItemProviderRegistry registry, File dataFolder) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.file = new File(Objects.requireNonNull(dataFolder, "dataFolder"), FILE_NAME);
  }

  public File file() {
    return file;
  }

  public boolean exists() {
    return file.isFile();
  }

  public boolean isRunning() {
    return RUNNING.get();
  }

  /**
   * Runs the export off the main thread and hands the result to {@code callback} on that same thread.
   * False when an export is already running or the async task could not be scheduled.
   */
  public boolean exportAsync(Consumer<CustomItemCatalogResult> callback) {
    if (!RUNNING.compareAndSet(false, true)) {
      return false;
    }

    boolean scheduled = SchedulerUtils.runAsync(plugin, () -> {
      CustomItemCatalogResult result;
      try {
        result = export();
      } finally {
        RUNNING.set(false);
      }
      if (callback != null) {
        callback.accept(result);
      }
    });

    if (!scheduled) {
      RUNNING.set(false);
    }
    return scheduled;
  }

  /**
   * Builds and writes the catalog on the calling thread. Call it off the main thread: only providers
   * that declare {@code requiresMainThread()} hop onto a tick, and only for as long as they enumerate.
   */
  public CustomItemCatalogResult export() {
    List<String> providers = new ArrayList<>();
    List<CustomItemEntry> items = new ArrayList<>();
    int discarded = 0;

    for (ItemProvider provider : registry.active()) {
      ProviderHarvest harvest = harvest(provider);
      discarded += harvest.discarded();
      if (harvest.entries().isEmpty()) {
        continue;
      }
      providers.add(provider.id());
      items.addAll(harvest.entries());
    }

    CustomItemCatalog catalog = new CustomItemCatalog(CustomItemCatalog.VERSION, System.currentTimeMillis(),
        List.copyOf(providers), List.copyOf(items));
    if (!write(catalog)) {
      return new CustomItemCatalogResult(false, providers.size(), items.size(), discarded, file.getPath());
    }

    HoloUI.log(Level.INFO, "[items] catalog written to %s providers=%d items=%d discarded=%d",
        file.getPath(), providers.size(), items.size(), discarded);
    return new CustomItemCatalogResult(true, providers.size(), items.size(), discarded, file.getPath());
  }

  private boolean write(CustomItemCatalog catalog) {
    String json = GSON.toJson(catalog);
    try {
      Path target = file.toPath();
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, json, StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException failure) {
      HoloUI.logExceptionStack(true, failure, "[items] failed to write the custom item catalog to %s:", file.getPath());
      return false;
    }
    mirrorIntoBuilderBundle(json);
    return true;
  }

  /**
   * The editor fetches exactly one catalog URL, and its build ships an empty file there so a visitor
   * never sees a 404 in the console. Overwriting that asset in the served bundle is what makes the
   * self-hosted editor pick the export up on its own. The bundle is wiped whenever the builder
   * updates, so a missing directory here is normal and not worth reporting.
   */
  private void mirrorIntoBuilderBundle(String json) {
    Path bundle = file.toPath().resolveSibling("builder").resolve("assets").resolve("catalog");
    if (!Files.isDirectory(bundle.getParent().getParent())) {
      return;
    }
    try {
      Files.createDirectories(bundle);
      Files.writeString(bundle.resolve(FILE_NAME), json, StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException ignored) {
      HoloUI.log(Level.FINE, "[items] could not mirror the catalog into the builder bundle.");
    }
  }

  private ProviderHarvest harvest(ItemProvider provider) {
    if (!provider.requiresMainThread() || Bukkit.isPrimaryThread()) {
      return harvestDirect(provider);
    }
    return harvestOnMainThread(provider);
  }

  private ProviderHarvest harvestOnMainThread(ItemProvider provider) {
    CompletableFuture<ProviderHarvest> harvested = new CompletableFuture<>();
    if (!SchedulerUtils.runGlobal(plugin, () -> harvested.complete(harvestDirect(provider)))) {
      HoloUI.log(Level.WARNING, "[items] provider %s needs the main thread and could not be scheduled, it is not in the catalog", provider.id());
      return ProviderHarvest.EMPTY;
    }

    try {
      return harvested.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return ProviderHarvest.EMPTY;
    } catch (Exception failure) {
      HoloUI.logExceptionStack(false, failure, "[items] provider %s did not finish enumerating on the main thread:", provider.id());
      return ProviderHarvest.EMPTY;
    }
  }

  private ProviderHarvest harvestDirect(ItemProvider provider) {
    List<String> ids = listIds(provider);
    if (ids.isEmpty()) {
      return ProviderHarvest.EMPTY;
    }

    List<CustomItemEntry> entries = new ArrayList<>(Math.min(ids.size(), MAX_ITEMS_PER_PROVIDER));
    int discarded = 0;
    for (String id : ids) {
      if (entries.size() >= MAX_ITEMS_PER_PROVIDER) {
        HoloUI.log(Level.WARNING, "[items] provider %s exposes %d ids, the catalog keeps the first %d",
            provider.id(), ids.size(), MAX_ITEMS_PER_PROVIDER);
        break;
      }

      CustomItemEntry entry = probe(provider, id);
      if (entry == null) {
        discarded++;
        continue;
      }
      entries.add(entry);
    }
    return new ProviderHarvest(List.copyOf(entries), discarded);
  }

  private List<String> listIds(ItemProvider provider) {
    Collection<String> ids;
    try {
      if (!provider.isReady()) {
        return List.of();
      }
      ids = provider.listIds();
    } catch (Throwable failure) {
      HoloUI.logExceptionStack(false, failure, "[items] provider %s failed to enumerate its ids:", provider.id());
      return List.of();
    }

    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    List<String> cleaned = new ArrayList<>(ids.size());
    for (String id : ids) {
      if (id != null && !id.isBlank()) {
        cleaned.add(id.trim());
      }
    }
    // sorted so two exports of the same server produce the same file, and so truncation is deterministic
    cleaned.sort(String::compareTo);
    return cleaned;
  }

  private CustomItemEntry probe(ItemProvider provider, String itemId) {
    ItemStack stack;
    try {
      // straight off the provider, going through the registry would fill the icon prototype cache with ids nobody opened
      stack = provider.resolve(itemId);
    } catch (Throwable failure) {
      return null;
    }

    if (stack == null || stack.getType() == Material.AIR) {
      return null;
    }
    return new CustomItemEntry(provider.id(), itemId, displayName(provider, itemId), stack.getType().getKey().getKey());
  }

  private static String displayName(ItemProvider provider, String itemId) {
    String raw;
    try {
      raw = provider.displayName(itemId);
    } catch (Throwable failure) {
      return itemId;
    }

    if (raw == null) {
      return itemId;
    }
    // the editor draws the name as plain text, legacy section colours would show up literally
    String plain = LEGACY_COLOR.matcher(raw).replaceAll("").trim();
    return plain.isEmpty() ? itemId : plain;
  }

  private record ProviderHarvest(List<CustomItemEntry> entries, int discarded) {
    private static final ProviderHarvest EMPTY = new ProviderHarvest(List.of(), 0);
  }
}
