package art.arcane.holoui.importer;

import java.util.List;
import java.util.Objects;

public record LegacyImportPlan(LegacyImportSource source, String sourcePath, boolean sourcePresent,
                               List<LegacyImportCandidate> candidates,
                               List<LegacyImportIssue> issues) {
  public LegacyImportPlan {
    source = Objects.requireNonNull(source, "source");
    sourcePath = Objects.requireNonNull(sourcePath, "sourcePath");
    candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
  }

  public long readyCount() {
    return candidates.stream()
        .filter(candidate -> candidate.disposition() == LegacyImportDisposition.READY)
        .count();
  }

  public long resumeCount() {
    return candidates.stream()
        .filter(candidate -> candidate.disposition() == LegacyImportDisposition.RESUME_BOARD)
        .count();
  }

  public long conflictCount() {
    return candidates.stream()
        .filter(candidate -> candidate.disposition() == LegacyImportDisposition.CONFLICT)
        .count();
  }

  public long errorCount() {
    return issues.stream()
        .filter(issue -> issue.severity() == LegacyImportIssue.Severity.ERROR)
        .count();
  }

  public long warningCount() {
    long planWarnings = issues.stream()
        .filter(issue -> issue.severity() == LegacyImportIssue.Severity.WARNING)
        .count();
    long candidateWarnings = candidates.stream().mapToLong(candidate -> candidate.warnings().size()).sum();
    return planWarnings + candidateWarnings;
  }
}
