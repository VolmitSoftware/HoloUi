package art.arcane.holoui.importer;

public record LegacyImportApplyEntry(String legacyId, Status status, String message) {
  public LegacyImportApplyEntry {
    if (legacyId == null || legacyId.isBlank()) {
      throw new IllegalArgumentException("legacyId must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    message = message == null ? "" : message;
  }

  public enum Status {
    IMPORTED,
    RESUMED,
    SKIPPED,
    FAILED,
    MENU_ONLY
  }
}
