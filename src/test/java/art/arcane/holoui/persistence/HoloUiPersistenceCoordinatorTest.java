package art.arcane.holoui.persistence;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HoloUiPersistenceCoordinatorTest {
  @Test
  public void externalTransactionPausesWatchersAndSerializesOrdinaryWriters() throws Exception {
    HoloUiPersistenceCoordinator coordinator = new HoloUiPersistenceCoordinator();
    HoloUiPersistenceCoordinator.ExternalTransaction transaction =
        coordinator.beginExternalTransaction();
    CountDownLatch entered = new CountDownLatch(1);
    AtomicBoolean completed = new AtomicBoolean();
    Thread writer = new Thread(() -> {
      try {
        coordinator.write(() -> {
          entered.countDown();
          completed.set(true);
          return null;
        });
      } catch (Exception failure) {
        throw new AssertionError(failure);
      }
    });
    writer.start();

    assertTrue(coordinator.watcherPaused());
    assertFalse(entered.await(100L, TimeUnit.MILLISECONDS));
    transaction.close();
    writer.join(5000L);

    assertFalse(coordinator.watcherPaused());
    assertTrue(completed.get());
  }

  @Test
  public void externalTransactionCannotBeginDuringAWatcherRead() throws Exception {
    HoloUiPersistenceCoordinator coordinator = new HoloUiPersistenceCoordinator();
    CountDownLatch readerEntered = new CountDownLatch(1);
    CountDownLatch releaseReader = new CountDownLatch(1);
    CountDownLatch transactionEntered = new CountDownLatch(1);
    Thread reader = new Thread(() -> coordinator.tryRead(() -> {
      readerEntered.countDown();
      try {
        if (!releaseReader.await(5L, TimeUnit.SECONDS)) {
          throw new AssertionError("reader release timed out");
        }
      } catch (InterruptedException interruption) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interruption);
      }
    }));
    reader.start();
    assertTrue(readerEntered.await(5L, TimeUnit.SECONDS));

    Thread transaction = new Thread(() -> {
      try (HoloUiPersistenceCoordinator.ExternalTransaction ignored =
               coordinator.beginExternalTransaction()) {
        transactionEntered.countDown();
      } catch (InterruptedException interruption) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interruption);
      }
    });
    transaction.start();
    assertFalse(transactionEntered.await(100L, TimeUnit.MILLISECONDS));
    releaseReader.countDown();
    reader.join(5000L);
    transaction.join(5000L);

    assertTrue(transactionEntered.getCount() == 0L);
    assertFalse(coordinator.watcherPaused());
  }
}
