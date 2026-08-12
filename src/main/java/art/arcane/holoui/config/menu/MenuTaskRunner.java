package art.arcane.holoui.config.menu;

import java.util.concurrent.TimeUnit;

@FunctionalInterface
interface MenuTaskRunner {
  MenuTaskHandle submit(Runnable task);

  default void shutdown() {
  }

  default boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return true;
  }

  @FunctionalInterface
  interface MenuTaskHandle {
    void cancel();
  }
}
