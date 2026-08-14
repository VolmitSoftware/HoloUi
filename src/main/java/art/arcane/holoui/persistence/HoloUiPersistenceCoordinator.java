package art.arcane.holoui.persistence;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HoloUiPersistenceCoordinator {
  private final Semaphore writePermit = new Semaphore(1, true);
  private final AtomicBoolean watcherPaused = new AtomicBoolean();
  private final AtomicBoolean recoveryRequired = new AtomicBoolean();

  public <T> T write(CheckedOperation<T> operation) throws Exception {
    CheckedOperation<T> requiredOperation = Objects.requireNonNull(operation, "operation");
    requireHealthy();
    writePermit.acquire();
    try {
      requireHealthy();
      return requiredOperation.execute();
    } finally {
      writePermit.release();
    }
  }

  public ExternalTransaction beginExternalTransaction() throws InterruptedException {
    requireHealthy();
    writePermit.acquire();
    if (recoveryRequired.get()) {
      writePermit.release();
      throw recoveryRequiredException();
    }
    if (!watcherPaused.compareAndSet(false, true)) {
      writePermit.release();
      throw new IllegalStateException("an external persistence transaction is already active");
    }
    return new ExternalTransaction(this);
  }

  public boolean tryRead(Runnable operation) {
    Runnable requiredOperation = Objects.requireNonNull(operation, "operation");
    if (recoveryRequired.get()) {
      return false;
    }
    if (!writePermit.tryAcquire()) {
      return false;
    }
    try {
      if (watcherPaused.get() || recoveryRequired.get()) {
        return false;
      }
      requiredOperation.run();
      return true;
    } finally {
      writePermit.release();
    }
  }

  public boolean watcherPaused() {
    return watcherPaused.get();
  }

  public void requireRestartRecovery() {
    recoveryRequired.set(true);
  }

  public boolean recoveryRequired() {
    return recoveryRequired.get();
  }

  private void requireHealthy() {
    if (recoveryRequired.get()) {
      throw recoveryRequiredException();
    }
  }

  private static IllegalStateException recoveryRequiredException() {
    return new IllegalStateException(
        "HoloUI persistence is quarantined until restart recovery completes");
  }

  private void endExternalTransaction() {
    if (!watcherPaused.compareAndSet(true, false)) {
      throw new IllegalStateException("external persistence transaction is not active");
    }
    writePermit.release();
  }

  @FunctionalInterface
  public interface CheckedOperation<T> {
    T execute() throws Exception;
  }

  public static final class ExternalTransaction implements AutoCloseable {
    private final HoloUiPersistenceCoordinator owner;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private ExternalTransaction(HoloUiPersistenceCoordinator owner) {
      this.owner = owner;
    }

    @Override
    public void close() {
      if (open.compareAndSet(true, false)) {
        owner.endExternalTransaction();
      }
    }
  }
}
