package art.arcane.holoui.editor.sync;

import java.time.Duration;
import java.time.Instant;

final class EditorSyncPollBackoff {
  private static final long MINIMUM_SECONDS = 3L;
  private static final long MAXIMUM_SECONDS = 300L;

  private int failures;
  private Instant nextAttempt = Instant.EPOCH;

  boolean ready(Instant now) {
    return !now.isBefore(nextAttempt);
  }

  Duration fail(String sessionId, int configuredPollSeconds, Instant now) {
    failures = Math.min(failures + 1, 31);
    long initial = Math.max(MINIMUM_SECONDS, configuredPollSeconds);
    int exponent = Math.min(failures - 1, 20);
    long unbounded = initial > (Long.MAX_VALUE >> exponent)
        ? Long.MAX_VALUE
        : initial << exponent;
    long base = Math.min(MAXIMUM_SECONDS, unbounded);
    int hash = Math.floorMod(sessionId.hashCode() * 31 + failures, 21) - 10;
    long jittered = Math.max(MINIMUM_SECONDS, Math.min(MAXIMUM_SECONDS,
        Math.round(base * (1.0D + hash / 100.0D))));
    nextAttempt = now.plusSeconds(jittered);
    return Duration.ofSeconds(jittered);
  }

  int failures() {
    return failures;
  }
}
