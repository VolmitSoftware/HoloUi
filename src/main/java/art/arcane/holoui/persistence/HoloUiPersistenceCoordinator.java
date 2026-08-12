package art.arcane.holoui.persistence;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HoloUiPersistenceCoordinator {
  private final Semaphore writePermit = new Semaphore(1, true);
  private final AtomicBoolean watcherPaused = new AtomicBoolean();

  public <T> T write(CheckedOperation<T> operation) throws Exception {
    CheckedOperation<T> requiredOperation = Objects.requireNonNull(operation, "operation");
    writePermit.acquire();
    try {
      return requiredOperation.execute();
    } finally {
      writePermit.release();
    }
  }

  public ExternalTransaction beginExternalTransaction() throws InterruptedException {
    writePermit.acquire();
    if (!watcherPaused.compareAndSet(false, true)) {
      writePermit.release();
      throw new IllegalStateException("an external persistence transaction is already active");
    }
    return new ExternalTransaction(this);
  }

  public boolean tryRead(Runnable operation) {
    Runnable requiredOperation = Objects.requireNonNull(operation, "operation");
    if (!writePermit.tryAcquire()) {
      return false;
    }
    try {
      if (watcherPaused.get()) {
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
