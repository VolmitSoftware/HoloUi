package art.arcane.holoui;

import art.arcane.holoui.config.ConfigManager;
import art.arcane.holoui.integration.ItemProvider;
import art.arcane.holoui.integration.ItemProviderRegistry;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.holoui.service.HoloUiTelemetry;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SingleLineChart;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Owns every bStats type so the main class never references them. bStats is slimjar-provided:
 * any chart code in HoloUI.class makes the verifier resolve the relocated CustomChart during
 * main-class linking, before ApplicationBuilder.build() has injected the library (boot NCDFE).
 */
public final class HoloUiMetrics {
  private Metrics metrics;

  private HoloUiMetrics(Metrics metrics) {
    this.metrics = metrics;
  }

  public static HoloUiMetrics start(HoloUI plugin, int pluginId) {
    HoloUiMetrics runtime = new HoloUiMetrics(new Metrics(plugin, pluginId));
    runtime.registerCharts(plugin);
    return runtime;
  }

  /**
   * bStats runs these callables on its own daemon thread, never the server thread, so every read
   * here has to hit concurrent or immutable state. Returning null skips the sample for that cycle.
   */
  private void registerCharts(HoloUI plugin) {
    metrics.addCustomChart(new SingleLineChart("menu_definitions", () -> {
      ConfigManager configs = plugin.getConfigManager();
      return configs == null ? null : configs.keys().size();
    }));
    metrics.addCustomChart(new SingleLineChart("open_menus", HoloUiTelemetry::menusOpen));
    metrics.addCustomChart(new SingleLineChart("session_holders", () -> {
      MenuSessionManager sessions = plugin.getSessionManager();
      return sessions == null ? null : sessions.holderCount();
    }));
    metrics.addCustomChart(new SingleLineChart("display_entities", DisplayEntityManager::totalCount));
    metrics.addCustomChart(new AdvancedPie("item_providers", () -> {
      ItemProviderRegistry registry = plugin.getItemProviders();
      if (registry == null) {
        return null;
      }
      // active() hands back an immutable snapshot and every id() is a constant, so this is off-thread safe
      Collection<ItemProvider> active = registry.active();
      Map<String, Integer> counts = new HashMap<>(active.size());
      for (ItemProvider provider : active) {
        counts.put(provider.id(), 1);
      }
      return counts;
    }));
  }

  public void shutdown() {
    Metrics activeMetrics = metrics;
    metrics = null;
    if (activeMetrics != null) {
      activeMetrics.shutdown();
    }
  }
}
