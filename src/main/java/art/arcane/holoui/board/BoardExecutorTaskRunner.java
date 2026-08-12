package art.arcane.holoui.board;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class BoardExecutorTaskRunner implements BoardTaskRunner {
  private final ExecutorService executor;

  BoardExecutorTaskRunner(ClassLoader contextClassLoader) {
    ClassLoader requiredClassLoader = Objects.requireNonNull(contextClassLoader, "contextClassLoader");
    AtomicInteger sequence = new AtomicInteger();
    this.executor = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "HoloUi-Board-Storage-" + sequence.incrementAndGet());
      thread.setDaemon(true);
      thread.setContextClassLoader(requiredClassLoader);
      return thread;
    });
  }

  @Override
  public BoardTaskHandle submit(Runnable task) {
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
