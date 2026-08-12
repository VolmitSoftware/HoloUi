package art.arcane.holoui.config.menu;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class MenuExecutorTaskRunner implements MenuTaskRunner {
  private final ExecutorService executor;

  MenuExecutorTaskRunner(ClassLoader contextClassLoader) {
    ClassLoader requiredClassLoader = Objects.requireNonNull(contextClassLoader, "contextClassLoader");
    AtomicInteger sequence = new AtomicInteger();
    this.executor = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "HoloUi-Menu-Storage-" + sequence.incrementAndGet());
      thread.setDaemon(true);
      thread.setContextClassLoader(requiredClassLoader);
      return thread;
    });
  }

  @Override
  public MenuTaskHandle submit(Runnable task) {
    Future<?> future = executor.submit(Objects.requireNonNull(task, "task"));
    return () -> future.cancel(false);
  }

  @Override
  public void shutdown() {
    executor.shutdown();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executor.awaitTermination(timeout, unit);
  }
}
