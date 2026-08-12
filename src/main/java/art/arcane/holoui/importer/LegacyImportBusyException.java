package art.arcane.holoui.importer;

public final class LegacyImportBusyException extends IllegalStateException {
  public LegacyImportBusyException() {
    super("another legacy hologram import operation is already running");
  }
}
