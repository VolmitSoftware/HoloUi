package art.arcane.holoui.service;

import art.arcane.holoui.BuilderServer;
import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.ConfigManager;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.menu.MenuSessionManager;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class HoloUiIntegrationService implements IntegrationServiceContract {
  private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
      new IntegrationProtocolVersion(1, 0),
      new IntegrationProtocolVersion(1, 1)
  );
  private static final Set<String> CAPABILITIES = Set.of(
      "handshake",
      "heartbeat",
      "metrics"
  );

  private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

  public void register() {
    Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, HoloUI.INSTANCE, ServicePriority.Normal);
    HoloUI.log(Level.INFO, "Integration provider registered for HoloUi");
  }

  public void unregister() {
    Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
    HoloUiTelemetry.clear();
  }

  @Override
  public String pluginId() {
    return "holoui";
  }

  @Override
  public String pluginVersion() {
    return HoloUI.INSTANCE.getDescription().getVersion();
  }

  @Override
  public Set<IntegrationProtocolVersion> supportedProtocols() {
    return SUPPORTED_PROTOCOLS;
  }

  @Override
  public Set<String> capabilities() {
    return CAPABILITIES;
  }

  @Override
  public Set<IntegrationMetricDescriptor> metricDescriptors() {
    return IntegrationMetricSchema.descriptors().stream()
        .filter(descriptor -> descriptor.key().startsWith("holoui."))
        .collect(Collectors.toSet());
  }

  @Override
  public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
    long now = System.currentTimeMillis();
    if (request == null) {
      return new IntegrationHandshakeResponse(
          pluginId(), pluginVersion(), false, null,
          SUPPORTED_PROTOCOLS, CAPABILITIES, "missing request", now
      );
    }

    Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
        SUPPORTED_PROTOCOLS,
        request.supportedProtocols()
    );
    if (negotiated.isEmpty()) {
      return new IntegrationHandshakeResponse(
          pluginId(), pluginVersion(), false, null,
          SUPPORTED_PROTOCOLS, CAPABILITIES, "no-common-protocol", now
      );
    }

    negotiatedProtocol = negotiated.get();
    return new IntegrationHandshakeResponse(
        pluginId(), pluginVersion(), true, negotiatedProtocol,
        SUPPORTED_PROTOCOLS, CAPABILITIES, "ok", now
    );
  }

  @Override
  public IntegrationHeartbeat heartbeat() {
    return new IntegrationHeartbeat(negotiatedProtocol, true, System.currentTimeMillis(), "ok");
  }

  @Override
  public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
    Set<String> requested = metricKeys == null || metricKeys.isEmpty()
        ? IntegrationMetricSchema.holouiKeys()
        : metricKeys;
    long now = System.currentTimeMillis();
    Map<String, IntegrationMetricSample> out = new HashMap<>();

    for (String key : requested) {
      switch (key) {
        case IntegrationMetricSchema.HOLOUI_SESSION_HOLDERS ->
            out.put(key, sampleSessionHolders(now));
        case IntegrationMetricSchema.HOLOUI_MENUS_OPEN ->
            out.put(key, available(key, HoloUiTelemetry.menusOpen(), now));
        case IntegrationMetricSchema.HOLOUI_PREVIEWS_OPEN ->
            out.put(key, available(key, HoloUiTelemetry.previewsOpen(), now));
        case IntegrationMetricSchema.HOLOUI_DISPLAY_ENTITIES ->
            out.put(key, available(key, DisplayEntityManager.totalCount(), now));
        case IntegrationMetricSchema.HOLOUI_DISPLAY_ENTITIES_VISIBLE ->
            out.put(key, available(key, DisplayEntityManager.visibleCount(), now));
        case IntegrationMetricSchema.HOLOUI_MENU_DEFINITIONS ->
            out.put(key, sampleMenuDefinitions(now));
        case IntegrationMetricSchema.HOLOUI_PACKETS_PER_SECOND ->
            out.put(key, available(key, HoloUiTelemetry.packetsPerSecond(now), now));
        case IntegrationMetricSchema.HOLOUI_SPAWNS_PER_SECOND ->
            out.put(key, available(key, HoloUiTelemetry.spawnsPerSecond(now), now));
        case IntegrationMetricSchema.HOLOUI_TICK_MS ->
            out.put(key, available(key, HoloUiTelemetry.tickMsPerSecond(now), now));
        case IntegrationMetricSchema.HOLOUI_PREVIEW_REFRESH_PER_SECOND ->
            out.put(key, available(key, HoloUiTelemetry.previewRefreshPerSecond(now), now));
        case IntegrationMetricSchema.HOLOUI_BUILDER_SERVER_RUNNING ->
            out.put(key, sampleBuilderServer(now));
        default -> out.put(key, IntegrationMetricSample.unavailable(
            IntegrationMetricSchema.descriptor(key),
            "unsupported-key",
            now
        ));
      }
    }

    return out;
  }

  private IntegrationMetricSample sampleSessionHolders(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HOLOUI_SESSION_HOLDERS);
    HoloUI plugin = HoloUI.INSTANCE;
    MenuSessionManager sessionManager = plugin == null ? null : plugin.getSessionManager();
    if (sessionManager == null) {
      return IntegrationMetricSample.unavailable(descriptor, "session-manager-not-ready", now);
    }

    return IntegrationMetricSample.available(descriptor, sessionManager.holderCount(), now);
  }

  private IntegrationMetricSample sampleMenuDefinitions(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HOLOUI_MENU_DEFINITIONS);
    HoloUI plugin = HoloUI.INSTANCE;
    ConfigManager configManager = plugin == null ? null : plugin.getConfigManager();
    if (configManager == null) {
      return IntegrationMetricSample.unavailable(descriptor, "config-manager-not-ready", now);
    }

    return IntegrationMetricSample.available(descriptor, configManager.keys().size(), now);
  }

  private IntegrationMetricSample sampleBuilderServer(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.HOLOUI_BUILDER_SERVER_RUNNING);
    HoloUI plugin = HoloUI.INSTANCE;
    BuilderServer builderServer = plugin == null ? null : plugin.getBuilderServer();
    if (builderServer == null) {
      return IntegrationMetricSample.unavailable(descriptor, "builder-server-not-ready", now);
    }

    return IntegrationMetricSample.available(descriptor, builderServer.isServerRunning() ? 1 : 0, now);
  }

  private IntegrationMetricSample available(String key, double value, long now) {
    return IntegrationMetricSample.available(IntegrationMetricSchema.descriptor(key), value, now);
  }
}
