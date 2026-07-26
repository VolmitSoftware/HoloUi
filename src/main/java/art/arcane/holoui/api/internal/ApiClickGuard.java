package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloClick;
import art.arcane.holoui.api.HoloClickHandler;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApiClickGuard {
  public static final int DEFAULT_FAULT_LIMIT = 5;
  public static final long DEFAULT_SLOW_MILLIS = 5L;

  private static final long SLOW_WARN_INTERVAL_MILLIS = 60_000L;

  private final Logger logger;
  private final LongSupplier clock;
  private final int faultLimit;
  private final long slowMillis;

  private final Map<String, AtomicInteger> faults = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> slowWarnedAt = new ConcurrentHashMap<>();
  private final Set<String> quarantined = ConcurrentHashMap.newKeySet();

  private final AtomicLong dispatchCount = new AtomicLong();
  private final AtomicLong faultCount = new AtomicLong();
  private final AtomicLong skipCount = new AtomicLong();
  private final AtomicLong quarantineCount = new AtomicLong();

  public ApiClickGuard(Logger logger, LongSupplier clock, int faultLimit, long slowMillis) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.faultLimit = faultLimit;
    this.slowMillis = slowMillis;
  }

  public ApiClickOutcome dispatch(ApiOwner owner, HoloClickHandler handler, HoloClick click) {
    if (handler == null) {
      return ApiClickOutcome.NO_HANDLER;
    }

    if (quarantined.contains(owner.name())) {
      skipCount.incrementAndGet();
      return ApiClickOutcome.SKIPPED_QUARANTINED;
    }

    long started = clock.getAsLong();

    try {
      if (!owner.active()) {
        skipCount.incrementAndGet();
        return ApiClickOutcome.SKIPPED_OWNER_DISABLED;
      }

      dispatchCount.incrementAndGet();
      handler.onClick(click);
      return ApiClickOutcome.DISPATCHED;
    } catch (Throwable error) {
      faultCount.incrementAndGet();
      logger.log(Level.WARNING, "HoloUi click handler from plugin '" + owner.name() + "' threw on component '"
          + click.componentId() + "' of menu '" + click.menuId() + "'", error);
      strike(owner);
      return ApiClickOutcome.FAULTED;
    } finally {
      observe(owner, clock.getAsLong() - started);
    }
  }

  public void forget(String ownerName) {
    quarantined.remove(ownerName);
    faults.remove(ownerName);
    slowWarnedAt.remove(ownerName);
  }

  public boolean quarantined(String ownerName) {
    return quarantined.contains(ownerName);
  }

  public long dispatches() {
    return dispatchCount.get();
  }

  public long faults() {
    return faultCount.get();
  }

  public long skips() {
    return skipCount.get();
  }

  public long quarantines() {
    return quarantineCount.get();
  }

  private void strike(ApiOwner owner) {
    if (faultLimit <= 0) {
      return;
    }

    int count = faults.computeIfAbsent(owner.name(), key -> new AtomicInteger()).incrementAndGet();

    if (count < faultLimit || !quarantined.add(owner.name())) {
      return;
    }

    quarantineCount.incrementAndGet();
    logger.severe("Disabled HoloUi click handlers from plugin '" + owner.name() + "' after " + count
        + " failures; its menus stay open but no longer dispatch clicks to it");
  }

  private void observe(ApiOwner owner, long elapsedMillis) {
    if (slowMillis <= 0L || elapsedMillis < slowMillis) {
      return;
    }

    long now = clock.getAsLong();
    AtomicLong last = slowWarnedAt.computeIfAbsent(owner.name(), key -> new AtomicLong());
    long previous = last.get();

    if (previous != 0L && now - previous < SLOW_WARN_INTERVAL_MILLIS) {
      return;
    }

    if (!last.compareAndSet(previous, now)) {
      return;
    }

    logger.warning("HoloUi click handler from plugin '" + owner.name() + "' spent " + elapsedMillis
        + "ms; click handlers run on the clicking player's region thread and must not block");
  }
}
