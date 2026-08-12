package art.arcane.holoui.editor.sync;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EditorSyncPollBackoffTest {
  @Test
  public void repeatedFailuresBackOffAndRecoverOnlyAtTheDeadline() {
    EditorSyncPollBackoff backoff = new EditorSyncPollBackoff();
    Instant now = Instant.parse("2026-08-12T12:00:00Z");
    Duration first = backoff.fail("abcdefghijkl-session", 3, now);
    assertFalse(backoff.ready(now.plus(first).minusMillis(1L)));
    assertTrue(backoff.ready(now.plus(first)));

    Duration second = backoff.fail("abcdefghijkl-session", 3, now.plus(first));
    assertTrue(second.compareTo(first) >= 0);
    assertTrue(backoff.failures() == 2);
  }

  @Test
  public void outageBackoffIsCappedAtFiveMinutesIncludingJitter() {
    EditorSyncPollBackoff backoff = new EditorSyncPollBackoff();
    Instant now = Instant.EPOCH;
    Duration delay = Duration.ZERO;
    for (int failure = 0; failure < 40; failure++) {
      delay = backoff.fail("abcdefghijkl-session", 60, now);
      now = now.plus(delay);
    }
    assertTrue(delay.compareTo(Duration.ofMinutes(5L)) <= 0);
  }
}
