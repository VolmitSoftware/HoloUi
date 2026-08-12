package art.arcane.holoui.board;

import java.util.concurrent.TimeUnit;

@FunctionalInterface
interface BoardTaskRunner {
  BoardTaskHandle submit(Runnable task);

  default void shutdown() {
  }

  default boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return true;
  }

  @FunctionalInterface
  interface BoardTaskHandle {
    void cancel();
  }
}
