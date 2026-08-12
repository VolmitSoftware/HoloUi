package art.arcane.holoui.importer;

public record LegacyImportIssue(Severity severity, String legacyId, String message) {
  public LegacyImportIssue {
    if (severity == null) {
      throw new IllegalArgumentException("severity must not be null");
    }
    legacyId = legacyId == null || legacyId.isBlank() ? "-" : legacyId;
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message must not be blank");
    }
  }

  public enum Severity {
    WARNING,
    ERROR
  }
}
