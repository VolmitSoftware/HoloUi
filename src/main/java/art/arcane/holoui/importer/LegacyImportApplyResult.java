package art.arcane.holoui.importer;

import java.util.List;
import java.util.Objects;

public record LegacyImportApplyResult(LegacyImportPlan plan, List<LegacyImportApplyEntry> entries) {
  public LegacyImportApplyResult {
    plan = Objects.requireNonNull(plan, "plan");
    entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
  }

  public long importedCount() {
    return count(LegacyImportApplyEntry.Status.IMPORTED)
        + count(LegacyImportApplyEntry.Status.RESUMED);
  }

  public long skippedCount() {
    return count(LegacyImportApplyEntry.Status.SKIPPED);
  }

  public long failedCount() {
    return count(LegacyImportApplyEntry.Status.FAILED)
        + count(LegacyImportApplyEntry.Status.MENU_ONLY);
  }

  private long count(LegacyImportApplyEntry.Status status) {
    return entries.stream().filter(entry -> entry.status() == status).count();
  }
}
