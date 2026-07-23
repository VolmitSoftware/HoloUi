package art.arcane.holoui.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class HoloUiTelemetry {
  private static final long RATE_WINDOW_MS = 1000L;
  private static final AtomicLong PACKETS = new AtomicLong();
  private static final AtomicLong SPAWN_CHURN = new AtomicLong();
  private static final AtomicLong PREVIEW_REFRESHES = new AtomicLong();
  private static final AtomicLong TICK_NANOS = new AtomicLong();
  private static final AtomicInteger MENUS_OPEN = new AtomicInteger();
  private static final AtomicInteger PREVIEWS_OPEN = new AtomicInteger();
  private static final Object RATE_LOCK = new Object();
  private static long windowStartMs;
  private static long windowPackets;
  private static long windowSpawnChurn;
  private static long windowPreviewRefreshes;
  private static long windowTickNanos;
  private static volatile double packetsPerSecond;
  private static volatile double spawnsPerSecond;
  private static volatile double previewRefreshPerSecond;
  private static volatile double tickMsPerSecond;

  private HoloUiTelemetry() {
  }

  public static void countPackets(long packets) {
    if (packets > 0L) {
      PACKETS.addAndGet(packets);
    }
  }

  public static void countSpawnChurn() {
    SPAWN_CHURN.incrementAndGet();
  }

  public static void countPreviewRefresh() {
    PREVIEW_REFRESHES.incrementAndGet();
  }

  public static void addTickNanos(long nanos) {
    if (nanos > 0L) {
      TICK_NANOS.addAndGet(nanos);
    }
  }

  public static void incrementMenusOpen() {
    MENUS_OPEN.incrementAndGet();
  }

  public static void decrementMenusOpen() {
    MENUS_OPEN.updateAndGet(current -> current > 0 ? current - 1 : 0);
  }

  public static void incrementPreviewsOpen() {
    PREVIEWS_OPEN.incrementAndGet();
  }

  public static void decrementPreviewsOpen() {
    PREVIEWS_OPEN.updateAndGet(current -> current > 0 ? current - 1 : 0);
  }

  public static int menusOpen() {
    return MENUS_OPEN.get();
  }

  public static int previewsOpen() {
    return PREVIEWS_OPEN.get();
  }

  public static double packetsPerSecond(long now) {
    refreshRates(now);
    return packetsPerSecond;
  }

  public static double spawnsPerSecond(long now) {
    refreshRates(now);
    return spawnsPerSecond;
  }

  public static double previewRefreshPerSecond(long now) {
    refreshRates(now);
    return previewRefreshPerSecond;
  }

  public static double tickMsPerSecond(long now) {
    refreshRates(now);
    return tickMsPerSecond;
  }

  public static void clear() {
    synchronized (RATE_LOCK) {
      PACKETS.set(0L);
      SPAWN_CHURN.set(0L);
      PREVIEW_REFRESHES.set(0L);
      TICK_NANOS.set(0L);
      windowStartMs = 0L;
      windowPackets = 0L;
      windowSpawnChurn = 0L;
      windowPreviewRefreshes = 0L;
      windowTickNanos = 0L;
      packetsPerSecond = 0D;
      spawnsPerSecond = 0D;
      previewRefreshPerSecond = 0D;
      tickMsPerSecond = 0D;
    }
    MENUS_OPEN.set(0);
    PREVIEWS_OPEN.set(0);
  }

  private static void refreshRates(long now) {
    synchronized (RATE_LOCK) {
      if (windowStartMs == 0L) {
        windowStartMs = now;
        windowPackets = PACKETS.get();
        windowSpawnChurn = SPAWN_CHURN.get();
        windowPreviewRefreshes = PREVIEW_REFRESHES.get();
        windowTickNanos = TICK_NANOS.get();
        return;
      }

      long elapsed = now - windowStartMs;
      if (elapsed < RATE_WINDOW_MS) {
        return;
      }

      long packets = PACKETS.get();
      long spawnChurn = SPAWN_CHURN.get();
      long previewRefreshes = PREVIEW_REFRESHES.get();
      long tickNanos = TICK_NANOS.get();
      double seconds = elapsed / 1000D;

      packetsPerSecond = (packets - windowPackets) / seconds;
      spawnsPerSecond = (spawnChurn - windowSpawnChurn) / seconds;
      previewRefreshPerSecond = (previewRefreshes - windowPreviewRefreshes) / seconds;
      tickMsPerSecond = ((tickNanos - windowTickNanos) / 1.0E6D) / seconds;

      windowStartMs = now;
      windowPackets = packets;
      windowSpawnChurn = spawnChurn;
      windowPreviewRefreshes = previewRefreshes;
      windowTickNanos = tickNanos;
    }
  }
}
