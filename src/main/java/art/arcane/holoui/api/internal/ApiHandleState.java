package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.HoloMenuState;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ApiHandleState {
  private final Logger logger;
  private final String ownerName;
  private final AtomicReference<Snapshot> snapshot =
      new AtomicReference<>(new Snapshot(HoloMenuState.PENDING, null));
  private final AtomicReference<Consumer<HoloCloseReason>> pendingCallback = new AtomicReference<>();

  public ApiHandleState(Logger logger, String ownerName) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
  }

  public HoloMenuState state() {
    return snapshot.get().state();
  }

  public HoloCloseReason reason() {
    return snapshot.get().reason();
  }

  public boolean live() {
    return !snapshot.get().state().terminal();
  }

  public boolean markOpen() {
    Snapshot current = snapshot.get();
    return current.state() == HoloMenuState.PENDING
        && snapshot.compareAndSet(current, new Snapshot(HoloMenuState.OPEN, null));
  }

  public boolean terminate(HoloCloseReason closeReason, boolean failed) {
    Snapshot target = new Snapshot(failed ? HoloMenuState.FAILED : HoloMenuState.CLOSED, closeReason);
    Snapshot current = snapshot.get();

    while (!current.state().terminal()) {
      if (snapshot.compareAndSet(current, target)) {
        fire(closeReason);
        return true;
      }

      current = snapshot.get();
    }

    return false;
  }

  public void onClosed(Consumer<HoloCloseReason> callback) {
    if (callback == null) {
      pendingCallback.set(null);
      return;
    }

    pendingCallback.set(callback);
    Snapshot current = snapshot.get();

    if (current.state().terminal()) {
      fire(current.reason());
    }
  }

  private void fire(HoloCloseReason closeReason) {
    Consumer<HoloCloseReason> taken = pendingCallback.getAndSet(null);

    if (taken == null) {
      return;
    }

    try {
      taken.accept(closeReason);
    } catch (Throwable error) {
      logger.log(Level.WARNING, "HoloUi menu close callback from plugin '" + ownerName + "' threw", error);
    }
  }

  private record Snapshot(HoloMenuState state, HoloCloseReason reason) {
  }
}
